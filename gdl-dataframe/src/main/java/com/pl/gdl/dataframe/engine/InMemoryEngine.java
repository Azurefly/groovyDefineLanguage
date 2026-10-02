package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.common.util.SqlSanitizer;
import com.pl.gdl.dataframe.dialect.H2SqlDialect;
import com.pl.gdl.dataframe.llm.LlmCallExecutor;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.advanced.GroovyCustomOperator;
import com.pl.gdl.dataframe.operator.advanced.LlmCallOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.*;
import java.util.*;

public class InMemoryEngine implements ExecutionEngine {
    private final JdbcDataSource dataSource;
    private final SqlPushdownEngine sqlEngine;
    private final Map<String, RowDataFrame> inMemoryTables = new LinkedHashMap<>();

    public InMemoryEngine() {
        this.dataSource = new JdbcDataSource();
        this.dataSource.setURL("jdbc:h2:mem:gdl_test_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1;MODE=LEGACY");
        this.sqlEngine = new SqlPushdownEngine(new H2SqlDialect());
    }

    public void registerTable(String tableName, RowDataFrame df) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName must not be blank");
        }
        Objects.requireNonNull(df, "df must not be null");

        String normalizedTableName = tableName.trim();
        createAndPopulateH2Table(normalizedTableName, df);
        inMemoryTables.put(normalizedTableName.toLowerCase(Locale.ROOT), df);
    }

    private void createAndPopulateH2Table(String tableName, RowDataFrame df) {
        if (df.getColumns().isEmpty()) return;
        validateDottedIdentifier(tableName);
        for (ColumnInfo col : df.getColumns()) {
            validateDottedIdentifier(col.getColumnName());
        }
        try (Connection conn = dataSource.getConnection()) {
            StringBuilder sb = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
            for (int i = 0; i < df.getColumns().size(); i++) {
                if (i > 0) sb.append(", ");
                ColumnInfo col = df.getColumns().get(i);
                sb.append(col.getColumnName()).append(" VARCHAR(500)");
            }
            sb.append(")");
            try (Statement stmt = conn.createStatement()) {
                // registerTable has replacement semantics: keep the direct in-memory view
                // and the SQL-backed view consistent, including when the schema changes.
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
                stmt.execute(sb.toString());
            }

            if (df.rowSize() > 0) {
                StringBuilder ins = new StringBuilder("INSERT INTO ").append(tableName).append(" VALUES (");
                for (int i = 0; i < df.getColumns().size(); i++) {
                    if (i > 0) ins.append(", ");
                    ins.append("?");
                }
                ins.append(")");
                try (PreparedStatement ps = conn.prepareStatement(ins.toString())) {
                    for (Row row : df) {
                        for (int i = 0; i < df.getColumns().size(); i++) {
                            Object val = row.getValue(df.getColumns().get(i).getColumnName());
                            ps.setObject(i + 1, val != null ? String.valueOf(val) : null);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to register H2 table: " + tableName, e);
        }
    }

    /**
     * 按点号逐段校验表名/列名是否为合法 SQL 标识符（字母/下划线开头，仅含字母数字下划线）。
     * <p>
     * 校验通过后直接使用原标识符拼接 SQL，刻意不加双引号：H2 的带引号标识符是大小写敏感的，
     * 而注册表名与查询中引用的表名大小写可能不一致（例如 registerTable 传入 "T_User" 而
     * FROM 写 "t_user"），加引号会导致查询时找不到表；不加引号时 H2 会将标识符统一折叠为
     * 大写，注册与查询两侧保持一致。标识符已通过白名单校验，直接拼接不存在注入风险。
     *
     * @throws IllegalArgumentException 标识符为空或任意一段不是合法标识符时抛出
     */
    private void validateDottedIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        for (String part : identifier.split("\\.")) {
            if (!SqlSanitizer.isValidIdentifier(part)) {
                throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
            }
        }
    }

    /**
     * 遍历异常链，判断是否为"表不存在"错误。
     * 识别依据：SQLState 以 {@code 42S02} 开头（SQL 标准表不存在），或 H2 错误码 42102。
     */
    private boolean isTableNotFound(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException se) {
                String sqlState = se.getSQLState();
                if (sqlState != null && sqlState.startsWith("42S02")) {
                    return true;
                }
                if (se.getErrorCode() == 42102) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        if (operator instanceof LlmCallOperator llmOp) {
            RowDataFrame input = !llmOp.getUpstream().isEmpty() ? execute(llmOp.getUpstream().get(0)) : new RowDataFrame();
            return new LlmCallExecutor().execute(llmOp, input);
        }

        if (operator instanceof GroovyCustomOperator groovyOp) {
            RowDataFrame input = !groovyOp.getUpstream().isEmpty() ? execute(groovyOp.getUpstream().get(0)) : new RowDataFrame();
            if (groovyOp.getClosure() != null) {
                return groovyOp.getClosure().call(input);
            }
            return input;
        }

        if (operator instanceof FromOperator fromOp) {
            String tbl = fromOp.getTableName().toLowerCase(Locale.ROOT);
            if (inMemoryTables.containsKey(tbl)) {
                return inMemoryTables.get(tbl);
            }
        }

        String sql = toSql(operator);
        if (sql == null || sql.isBlank()) {
            return new RowDataFrame();
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            boolean isQuery = sql.trim().toUpperCase(Locale.ROOT).startsWith("SELECT") ||
                    sql.trim().toUpperCase(Locale.ROOT).startsWith("WITH");
            if (isQuery) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    List<ColumnInfo> columns = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) {
                        ColumnInfo ci = new ColumnInfo(md.getColumnLabel(i), md.getColumnTypeName(i));
                        columns.add(ci);
                    }
                    RowDataFrame result = new RowDataFrame(columns);
                    while (rs.next()) {
                        Row row = new Row();
                        for (int i = 1; i <= cols; i++) {
                            row.setValue(md.getColumnLabel(i), rs.getObject(i));
                        }
                        result.addRow(row);
                    }
                    return result;
                }
            } else {
                stmt.execute(sql);
                return new RowDataFrame();
            }
        } catch (SQLException e) {
            // 仅当表不存在时返回空 DataFrame（兼容未注册表的模拟查询），其他 SQL 错误向上抛出，
            // 避免吞掉真正的语法错误、连接失败等问题导致静默产生空结果。
            if (isTableNotFound(e)) {
                return new RowDataFrame();
            }
            throw new GdlExecutionException("Failed to execute SQL: " + e.getMessage(), e);
        }
    }

    @Override
    public String toSql(LogicalOperator operator) {
        return sqlEngine.toSql(operator);
    }
}
