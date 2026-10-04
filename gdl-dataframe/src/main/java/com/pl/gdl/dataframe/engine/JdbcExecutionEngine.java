package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.DataQualityException;
import com.pl.gdl.dataframe.operator.base.DescribeOperator;
import com.pl.gdl.dataframe.operator.base.PivotOperator;
import com.pl.gdl.dataframe.operator.base.ValidateOperator;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executes generated/passthrough SQL against any JdbcDatasource using pooled connections. */
public class JdbcExecutionEngine extends SqlPushdownEngine {
    private final JdbcDatasource datasource;
    private final JdbcConnectionManager connectionManager;

    public JdbcExecutionEngine(JdbcDatasource datasource, SqlDialect dialect) {
        this(datasource, dialect, JdbcConnectionManager.getDefault());
    }

    public JdbcExecutionEngine(JdbcDatasource datasource, SqlDialect dialect, JdbcConnectionManager connectionManager) {
        super(Objects.requireNonNull(dialect, "dialect must not be null"));
        this.datasource = Objects.requireNonNull(datasource, "datasource must not be null");
        this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    }

    public JdbcDatasource getDatasource() {
        return datasource;
    }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        // 非纯 SQL 算子：JDBC 下推无法生成 SQL，走"上游下推 + 内存收尾"混合执行，
        // 语义与 InMemoryEngine 保持一致（validate 透传、describe/pivot 内存计算）。
        if (operator instanceof ValidateOperator validateOp) {
            return executeValidate(validateOp);
        }
        if (operator instanceof DescribeOperator describeOp) {
            RowDataFrame input = executeUpstreamOrEmpty(describeOp);
            return new InMemoryEngine().describeData(input);
        }
        if (operator instanceof PivotOperator pivotOp) {
            RowDataFrame input = executeUpstreamOrEmpty(pivotOp);
            return new InMemoryEngine().pivotData(input, pivotOp);
        }
        String sql = toSql(operator);
        if (sql == null || sql.isBlank()) return new RowDataFrame();

        try (Connection connection = connectionManager.getConnection(datasource)) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                RowDataFrame result = executeStatements(connection, splitStatements(sql));
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // Connection close/eviction will handle a broken connection.
                }
            }
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        } catch (GdlExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        }
    }

    /** 上游下推执行：无上游时返回空结果（与 InMemoryEngine 语义一致）。 */
    private RowDataFrame executeUpstreamOrEmpty(LogicalOperator operator) {
        if (operator.getUpstream().isEmpty()) return new RowDataFrame();
        return execute(operator.getUpstream().get(0));
    }

    /**
     * 数据质量检查：违规计数下推到数据库执行，全表只做一次 COUNT 查询；
     * 无违规时透传上游数据（与 InMemoryEngine 语义一致）。
     */
    private RowDataFrame executeValidate(ValidateOperator validateOp) {
        if (validateOp.getUpstream().isEmpty()) return new RowDataFrame();
        LogicalOperator upstream = validateOp.getUpstream().get(0);
        String countSql = "SELECT COUNT(*) FROM (" + toSql(upstream)
                + ") sub_validate WHERE NOT (" + validateOp.getCondition() + ")";
        long violations;
        try (Connection connection = connectionManager.getConnection(datasource);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(countSql)) {
            violations = rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC validate failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        }
        if (violations > 0) {
            throw new DataQualityException(validateOp.getMessage(), violations);
        }
        return execute(upstream);
    }

    private RowDataFrame executeStatements(Connection connection, List<String> statements) throws SQLException {
        RowDataFrame result = new RowDataFrame();
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                if (sql.isBlank()) continue;
                boolean hasResultSet = statement.execute(sql);
                if (hasResultSet) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        result = read(resultSet);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Split dialect-generated multi-statement SQL without breaking semicolons
     * inside string literals or quoted identifiers.
     */
    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (quote != 0) {
                current.append(ch);
                if (ch == quote) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                        current.append(sql.charAt(++i));
                    } else {
                        quote = 0;
                    }
                } else if (ch == '\\' && i + 1 < sql.length() && quote == '\'') {
                    current.append(sql.charAt(++i));
                }
                continue;
            }

            if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
            } else if (ch == ';') {
                addStatement(statements, current);
            } else {
                current.append(ch);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isEmpty()) statements.add(value);
        current.setLength(0);
    }

    private RowDataFrame read(ResultSet rs) throws SQLException {
        RowDataFrame frame = new RowDataFrame();
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        // 列元数据必须填充：下游 writeCsv/列引用/跨算子都依赖 columns，
        // 否则 JDBC 查询结果只有行没有列名（此前返回空列导致写 CSV 全是空行）。
        java.util.List<com.pl.gdl.common.model.ColumnInfo> columns = new java.util.ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isBlank()) label = meta.getColumnName(i);
            String typeName;
            try {
                typeName = meta.getColumnTypeName(i);
            } catch (SQLException ignored) {
                typeName = "UNKNOWN";
            }
            columns.add(new com.pl.gdl.common.model.ColumnInfo(label, typeName));
        }
        frame.setColumns(columns);
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= count; i++) {
                String label = meta.getColumnLabel(i);
                if (label == null || label.isBlank()) label = meta.getColumnName(i);
                row.put(label, rs.getObject(i));
            }
            frame.addRowValue(row);
        }
        return frame;
    }
}
