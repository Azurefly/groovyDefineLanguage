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
        // dry-run（纯规划）模式：不触碰数据源，直接返回空结果，杜绝任何副作用
        if (DryRun.isActive()) return new RowDataFrame();
        List<String> tempTables = new ArrayList<>();
        List<String> prevTables = activeTempTables.get();
        activeTempTables.set(tempTables);
        try (Connection connection = connectionManager.getConnection(datasource)) {
            Connection prevConn = activeConnection.get();
            activeConnection.set(connection);
            try {
                // 终端 describe：直接下推执行（多查询+组装，非单 SQL）
                if (operator instanceof DescribeOperator describeOp) {
                    return executeDescribePushdown(describeOp);
                }
                // 终端 pivot：两次查询下推（DISTINCT 取透视值 + CASE WHEN 聚合），O(1) 内存
                if (operator instanceof PivotOperator pivotOp) {
                    return executePivotPushdown(pivotOp);
                }
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
            // 大数据友好：describe 下推为聚合 SQL（单遍扫描、O(1) 内存），
            // 结果仅为列数行的小表，物化为临时表后上层继续纯 SQL
            return "SELECT * FROM " + materializeTempTable(executeDescribePushdown(describeOp));
        }
        if (upstream instanceof PivotOperator pivotOp) {
            // 大数据友好：pivot 下推为两次查询（小结果），物化为临时表后上层继续纯 SQL，
            // 不再全量拉取上游数据（此前 O(全表) 内存）
            return "SELECT * FROM " + materializeTempTable(executePivotPushdown(pivotOp));
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

    private static final java.util.Set<String> NUMERIC_TYPES = java.util.Set.of(
            "BIGINT", "INT", "INTEGER", "SMALLINT", "TINYINT", "LONG",
            "DOUBLE", "DOUBLE PRECISION", "FLOAT", "FLOAT4", "FLOAT8", "REAL",
            "DECIMAL", "NUMERIC", "NUMBER");

    /** describe 的 8 列输出结构（与 InMemoryEngine.describeData 一致）。 */
    private static RowDataFrame emptyDescribeFrame() {
        return new RowDataFrame(java.util.List.of(
                new ColumnInfo("column_name", "STRING"),
                new ColumnInfo("data_type", "STRING"),
                new ColumnInfo("row_count", "BIGINT"),
                new ColumnInfo("null_count", "BIGINT"),
                new ColumnInfo("distinct_count", "BIGINT"),
                new ColumnInfo("min_value", "STRING"),
                new ColumnInfo("max_value", "STRING"),
                new ColumnInfo("avg_value", "STRING")));
    }

    /**
     * describe 下推执行：上游数据不离库，按列分批做聚合查询
     * （COUNT/NULL/DISTINCT/MIN/MAX，数值列再加 AVG），Java 侧组装为
     * 与 InMemoryEngine.describeData 同格式的结果。
     * <p>大数据友好：单遍扫描、O(1) 内存，100 亿行宽表也安全；
     * 宽表按 50 列分批，避免单条 SQL 表达式过多。
     */
    private RowDataFrame executeDescribePushdown(DescribeOperator describeOp) {
        RowDataFrame output = emptyDescribeFrame();
        if (describeOp.getUpstream().isEmpty()) return output;
        String upstreamSql = toSqlUpstream(describeOp.getUpstream().get(0));
        if (upstreamSql == null || upstreamSql.isBlank()) return output;
        Connection conn = activeConnection.get();
        if (conn == null) {
            throw new UnsupportedOperationException(
                    "describe 下推需要 JDBC 连接（纯 SQL 生成模式不支持）");
        }
        String sourceSql = "SELECT * FROM (" + upstreamSql + ") sub_describe_src";
        try {
            // 零数据取列元数据（不传输任何行）
            List<ColumnInfo> cols = queryColumns(conn, sourceSql + " WHERE 1=0");
            if (cols.isEmpty()) return output;
            // 按 50 列分批聚合
            for (int from = 0; from < cols.size(); from += 50) {
                List<ColumnInfo> batch = cols.subList(from, Math.min(from + 50, cols.size()));
                runDescribeBatch(conn, sourceSql, batch, output);
            }
        } catch (SQLException e) {
            throw new GdlExecutionException(
                    "describe 下推执行失败: " + e.getMessage(), e);
        }
        return output;
    }

    /**
     * pivot 下推执行：两次查询，O(1) 内存。
     * <ol>
     *   <li>{@code SELECT DISTINCT pivotCol ...} 取透视值（小结果）；</li>
     *   <li>{@code SELECT groupCols, AGG(CASE WHEN pivotCol='v' THEN valueCol END) AS "v", ...}
     *       单遍扫描聚合。</li>
     * </ol>
     * 别名规则与 InMemoryEngine.pivotData 一致：原值加双引号、内嵌引号转义、
     * 冲突追加序号；结果列名统一转小写。
     */
    private RowDataFrame executePivotPushdown(
            com.pl.gdl.dataframe.operator.base.PivotOperator pivotOp) {
        if (pivotOp.getUpstream().isEmpty()) return new RowDataFrame();
        String upstreamSql = toSqlUpstream(pivotOp.getUpstream().get(0));
        if (upstreamSql == null || upstreamSql.isBlank()) return new RowDataFrame();
        Connection conn = activeConnection.get();
        if (conn == null) {
            throw new UnsupportedOperationException(
                    "pivot 下推需要 JDBC 连接（纯 SQL 生成模式不支持）");
        }
        String sourceSql = "SELECT * FROM (" + upstreamSql + ") sub_pivot_src";
        String pivotCol = simpleColRef(pivotOp.getPivotColumn());
        String valueCol = simpleColRef(pivotOp.getValueColumn());
        List<String> groupCols = pivotOp.getGroupByColumns();
        List<String> groupRefs = new ArrayList<>();
        for (String g : groupCols) groupRefs.add(simpleColRef(g));
        try {
            // 1. 透视值（去 NULL、确定性排序）
            List<String> pivotValues = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT DISTINCT " + pivotCol + " FROM (" + sourceSql + ") sub_pv" +
                         " WHERE " + pivotCol + " IS NOT NULL ORDER BY 1")) {
                while (rs.next()) pivotValues.add(rs.getString(1));
            }
            // 无透视值：返回仅分组列的空结果（旧内存版此处会生成非法 SQL）
            List<ColumnInfo> outCols = new ArrayList<>();
            for (String g : groupCols) outCols.add(new ColumnInfo(g.toLowerCase(java.util.Locale.ROOT), "STRING"));
            if (pivotValues.isEmpty()) return new RowDataFrame(outCols);
            // 2. 构建透视聚合 SQL
            StringBuilder sql = new StringBuilder("SELECT ");
            if (!groupRefs.isEmpty()) sql.append(String.join(", ", groupRefs)).append(", ");
            java.util.Set<String> usedAliases = new java.util.HashSet<>();
            for (int i = 0; i < pivotValues.size(); i++) {
                if (i > 0) sql.append(", ");
                String pv = pivotValues.get(i).replace("'", "''");
                String quoted = pivotValues.get(i).replace("\"", "\"\"");
                String alias = quoted;
                int suffix = 2;
                while (!usedAliases.add(alias)) alias = quoted + "_" + (suffix++);
                sql.append(pivotOp.getAggFunction())
                   .append("(CASE WHEN ").append(pivotCol)
                   .append(" = '").append(pv).append("' THEN ")
                   .append(valueCol).append(" END) AS \"").append(alias).append("\"");
            }
            sql.append(" FROM (").append(sourceSql).append(") sub_pivot");
            if (!groupRefs.isEmpty()) sql.append(" GROUP BY ").append(String.join(", ", groupRefs));
            // 3. 执行并组装（列名转小写，与 JDBC 引擎统一）
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql.toString())) {
                ResultSetMetaData md = rs.getMetaData();
                List<ColumnInfo> columns = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    String typeName;
                    try {
                        typeName = md.getColumnTypeName(i);
                    } catch (SQLException ignored) {
                        typeName = "UNKNOWN";
                    }
                    columns.add(new ColumnInfo(normLabel(md, i), typeName));
                }
                RowDataFrame result = new RowDataFrame(columns);
                while (rs.next()) {
                    Row row = new Row();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.setValue(normLabel(md, i), rs.getObject(i));
                    }
                    result.addRow(row);
                }
                return result;
            }
        } catch (SQLException e) {
            throw new GdlExecutionException(
                    "pivot 下推执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 列引用：简单小写标识符不加引号（H2 会把未加引号的标识符折叠为大写，
     * 与源表未加引号建表时的行为一致；加引号反而因大小写敏感找不到列），
     * 其余走方言引号。
     */
    private String simpleColRef(String col) {
        if (col != null && col.matches("[a-z_][a-z0-9_]*")) return col;
        return getDialect().quoteIdentifier(col);
    }

    /** 零数据查询，仅取列元数据。 */
    private List<ColumnInfo> queryColumns(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<ColumnInfo> cols = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                String typeName;
                try {
                    typeName = meta.getColumnTypeName(i);
                } catch (SQLException ignored) {
                    typeName = "UNKNOWN";
                }
                cols.add(new ColumnInfo(normLabel(meta, i), typeName));
            }
            return cols;
        }
    }

    /** 对一批列执行一次聚合查询，把结果组装进 output。 */
    private void runDescribeBatch(Connection conn, String sourceSql,
                                  List<ColumnInfo> batch, RowDataFrame output) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS rc");
        List<Boolean> numeric = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            ColumnInfo col = batch.get(i);
            String q = simpleColRef(col.getColumnName());
            boolean isNumeric = col.getDataTypeName() != null
                    && NUMERIC_TYPES.contains(col.getDataTypeName().toUpperCase(java.util.Locale.ROOT));
            numeric.add(isNumeric);
            sql.append(", COUNT(").append(q).append(") AS q").append(i).append("_nn");
            sql.append(", COUNT(DISTINCT ").append(q).append(") AS q").append(i).append("_dc");
            sql.append(", MIN(CAST(").append(q).append(" AS VARCHAR(500))) AS q").append(i).append("_min");
            sql.append(", MAX(CAST(").append(q).append(" AS VARCHAR(500))) AS q").append(i).append("_max");
            if (isNumeric) {
                sql.append(", AVG(CAST(").append(q).append(" AS DOUBLE)) AS q").append(i).append("_avg");
            }
        }
        sql.append(" FROM (").append(sourceSql).append(") sub_describe_batch");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql.toString())) {
            if (!rs.next()) return;
            long rowCount = rs.getLong("rc");
            for (int i = 0; i < batch.size(); i++) {
                ColumnInfo col = batch.get(i);
                long nonNull = rs.getLong("q" + i + "_nn");
                Row row = new Row();
                row.setValue("column_name", col.getColumnName());
                row.setValue("data_type", col.getDataTypeName());
                row.setValue("row_count", rowCount);
                row.setValue("null_count", rowCount - nonNull);
                row.setValue("distinct_count", rs.getLong("q" + i + "_dc"));
                row.setValue("min_value", rs.getString("q" + i + "_min"));
                row.setValue("max_value", rs.getString("q" + i + "_max"));
                if (numeric.get(i)) {
                    // 用 getString 而非 getObject().toString()，与内存版行为一致
                    //（如 H2 AVG 返回的 BigDecimal 两种取法格式不同）
                    row.setValue("avg_value", rs.getString("q" + i + "_avg"));
                } else {
                    row.setValue("avg_value", null);
                }
                output.addRow(row);
            }
        }
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
