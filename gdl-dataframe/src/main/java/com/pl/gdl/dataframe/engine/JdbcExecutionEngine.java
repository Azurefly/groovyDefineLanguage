package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.DataQualityException;
import com.pl.gdl.dataframe.operator.base.DescribeOperator;
import com.pl.gdl.dataframe.operator.base.PivotOperator;
import com.pl.gdl.dataframe.operator.base.SampleOperator;
import com.pl.gdl.dataframe.operator.base.ValidateOperator;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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

    /** 当前执行中的 JDBC 连接（toSqlUpstream 物化时复用，保证临时表可见）。 */
    private final ThreadLocal<Connection> activeConnection = new ThreadLocal<>();
    /** 当前执行中创建的临时表（execute finally 中清理，嵌套执行各自管理）。 */
    private final ThreadLocal<List<String>> activeTempTables =
            ThreadLocal.withInitial(ArrayList::new);

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        List<String> tempTables = new ArrayList<>();
        List<String> prevTables = activeTempTables.get();
        activeTempTables.set(tempTables);
        try (Connection connection = connectionManager.getConnection(datasource)) {
            Connection prevConn = activeConnection.get();
            activeConnection.set(connection);
            try {
                // 终端也可能是非 SQL 算子（如 validate 链尾），统一走拦截入口
                String sql = isNonSqlRoot(operator) ? toSqlUpstream(operator) : toSql(operator);
                if (sql == null || sql.isBlank()) return new RowDataFrame();

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
            } finally {
                activeConnection.set(prevConn);
            }
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        } catch (DataQualityException | GdlExecutionException e) {
            // 质量门禁与执行异常直接透出，不包一层，保证调用方能按类型捕获
            throw e;
        } catch (RuntimeException e) {
            throw new GdlExecutionException("JDBC execution failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        } finally {
            activeTempTables.set(prevTables);
            dropTempTables(tempTables);
        }
    }

    /** 终端算子本身是否需要走物化拦截（validate/describe/pivot/不支持 seed 的 sample）。 */
    private boolean isNonSqlRoot(LogicalOperator operator) {
        return operator instanceof ValidateOperator
                || operator instanceof DescribeOperator
                || operator instanceof PivotOperator
                || isSeededSampleUnsupported(operator);
    }

    private boolean isSeededSampleUnsupported(LogicalOperator operator) {
        return operator instanceof SampleOperator sampleOp
                && sampleOp.getSeed() != null
                && !getDialect().supportsSeededRandom();
    }

    /**
     * 上游递归拦截：链中任意位置的非 SQL 算子都在此处处理。
     * <ul>
     *   <li>validate：违规计数下推 SQL，通过则透传上游（无需物化）；</li>
     *   <li>describe/pivot/不支持 seed 的 sample：上游下推执行 → 内存计算 →
     *       物化为目标库临时表，上层继续走纯 SQL。</li>
     * </ul>
     * 不修改原算子树（分支复用同一上游时各自独立物化），临时表在 execute finally 清理。
     */
    @Override
    protected String toSqlUpstream(LogicalOperator upstream) {
        if (upstream == null) return "";
        if (upstream instanceof ValidateOperator validateOp) {
            return resolveValidate(validateOp);
        }
        if (upstream instanceof DescribeOperator describeOp) {
            RowDataFrame input = executeUpstreamOrEmpty(describeOp);
            return "SELECT * FROM " + materializeTempTable(new InMemoryEngine().describeData(input));
        }
        if (upstream instanceof PivotOperator pivotOp) {
            RowDataFrame input = executeUpstreamOrEmpty(pivotOp);
            return "SELECT * FROM " + materializeTempTable(new InMemoryEngine().pivotData(input, pivotOp));
        }
        if (isSeededSampleUnsupported(upstream)) {
            SampleOperator sampleOp = (SampleOperator) upstream;
            RowDataFrame input = executeUpstreamOrEmpty(sampleOp);
            return "SELECT * FROM " + materializeTempTable(seededSample(input, sampleOp));
        }
        return super.toSqlUpstream(upstream);
    }

    /** 上游下推执行：无上游时返回空结果（与 InMemoryEngine 语义一致）。 */
    private RowDataFrame executeUpstreamOrEmpty(LogicalOperator operator) {
        if (operator.getUpstream().isEmpty()) return new RowDataFrame();
        return execute(operator.getUpstream().get(0));
    }

    /**
     * 数据质量检查：违规计数下推到数据库执行，全表只做一次 COUNT 查询；
     * 无违规时直接返回上游 SQL（透传，无需物化）。
     */
    private String resolveValidate(ValidateOperator validateOp) {
        if (validateOp.getUpstream().isEmpty()) return "SELECT 1 WHERE 1=0";
        LogicalOperator upstream = validateOp.getUpstream().get(0);
        String subSql = toSqlUpstream(upstream);
        Connection connection = activeConnection.get();
        if (connection == null) {
            throw new UnsupportedOperationException(
                    "validate 无法在纯 SQL 生成模式下执行：需要 JDBC 连接做违规计数");
        }
        String countSql = "SELECT COUNT(*) FROM (" + subSql
                + ") sub_validate WHERE NOT (" + validateOp.getCondition() + ")";
        long violations;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(countSql)) {
            violations = rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new GdlExecutionException("JDBC validate failed for " + getDialect().getDialectName()
                    + ": " + e.getMessage(), e);
        }
        if (violations > 0) {
            throw new DataQualityException(validateOp.getMessage(), violations);
        }
        return subSql;
    }

    /**
     * 将内存数据集物化为目标库临时表（列类型沿用来源库原生类型名，同库建表合法），
     * 返回临时表名。DDL/DML 走当前执行连接，随主事务提交/回滚。
     */
    private String materializeTempTable(RowDataFrame data) {
        List<ColumnInfo> cols = data.getColumns();
        if (cols.isEmpty()) {
            throw new GdlExecutionException("无法物化空列数据集为临时表");
        }
        Connection connection = activeConnection.get();
        if (connection == null) {
            throw new UnsupportedOperationException(
                    "无法在纯 SQL 生成模式下物化临时表：需要 JDBC 连接");
        }
        String tableName = "gdl_tmp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(tableName).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) ddl.append(", ");
            String name = cols.get(i).getColumnName();
            // 简单小写标识符不加引号（H2 会统一折叠，上下游引用一致）；
            // 非常规名才加引号（此前按名引用本就不可靠，属尽力而为）。
            if (!name.matches("[a-z_][a-z0-9_]*")) {
                name = getDialect().quoteIdentifier(name);
            }
            // DDL 类型走通用规范化（原生类型名如 STRING 在 H2 上不合法），
            // 数据经 setObject 按 Java 类型写入，无需精确类型对应
            String type = InMemoryEngine.toCanonicalDdlType(cols.get(i).getDataTypeName());
            ddl.append(name).append(" ").append(type);
        }
        ddl.append(")");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
            stmt.execute(ddl.toString());
            if (!data.getRows().isEmpty()) {
                String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + tableName + " VALUES (" + placeholders + ")")) {
                    for (Row row : data.getRows()) {
                        for (int i = 0; i < cols.size(); i++) {
                            ps.setObject(i + 1, row.getValue(cols.get(i).getColumnName()));
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new GdlExecutionException(
                    "物化临时表失败 " + tableName + ": " + e.getMessage(), e);
        }
        activeTempTables.get().add(tableName);
        return tableName;
    }

    private void dropTempTables(List<String> tables) {
        if (tables == null || tables.isEmpty()) return;
        try (Connection conn = connectionManager.getConnection(datasource);
             Statement stmt = conn.createStatement()) {
            for (String t : tables) {
                try {
                    stmt.execute("DROP TABLE IF EXISTS " + t);
                } catch (SQLException ignored) {
                    // 清理尽力而为，不掩盖主流程异常
                }
            }
        } catch (Exception ignored) {
            // 清理尽力而为，不掩盖主流程异常
        }
    }

    /** 确定性采样：Java 侧按 seed 洗牌后取前 N 行（用于方言不支持 seed 的情况）。 */
    private RowDataFrame seededSample(RowDataFrame data, SampleOperator sampleOp) {
        List<Row> rows = new ArrayList<>(data.getRows());
        Collections.shuffle(rows, new java.util.Random(sampleOp.getSeed()));
        int n = sampleOp.isBySize()
                ? sampleOp.getSampleSize()
                : (int) Math.round(rows.size() * sampleOp.getFraction());
        n = Math.max(0, Math.min(n, rows.size()));
        return new RowDataFrame(data.getColumns(), new ArrayList<>(rows.subList(0, n)));
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

    /**
     * 列名规范化：统一转小写。H2 会把未加引号的标识符折叠为大写（如 CNT），
     * SQLite/MySQL 则保留原样；统一小写后同一 GDL 在不同数据源上看到的列名一致，
     * 实现"用户无感知数据源"。
     */
    private static String normLabel(ResultSetMetaData meta, int i) throws SQLException {
        String label = meta.getColumnLabel(i);
        if (label == null || label.isBlank()) label = meta.getColumnName(i);
        if (label == null || label.isBlank()) label = "c" + i;
        return label.toLowerCase(Locale.ROOT);
    }

    private RowDataFrame read(ResultSet rs) throws SQLException {
        RowDataFrame frame = new RowDataFrame();
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        // 列元数据必须填充：下游 writeCsv/列引用/跨算子都依赖 columns，
        // 否则 JDBC 查询结果只有行没有列名（此前返回空列导致写 CSV 全是空行）。
        java.util.List<com.pl.gdl.common.model.ColumnInfo> columns = new java.util.ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String typeName;
            try {
                typeName = meta.getColumnTypeName(i);
            } catch (SQLException ignored) {
                typeName = "UNKNOWN";
            }
            columns.add(new com.pl.gdl.common.model.ColumnInfo(normLabel(meta, i), typeName));
        }
        frame.setColumns(columns);
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= count; i++) {
                row.put(normLabel(meta, i), rs.getObject(i));
            }
            frame.addRowValue(row);
        }
        return frame;
    }
}
