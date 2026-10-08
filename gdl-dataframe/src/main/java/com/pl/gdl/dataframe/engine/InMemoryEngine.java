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
        }        Objects.requireNonNull(df, "df must not be null");

        String normalizedTableName = tableName.trim();
        createAndPopulateH2Table(normalizedTableName, df);
        inMemoryTables.put(normalizedTableName.toLowerCase(Locale.ROOT), df);
    }

    /**
     * 注册用户自定义函数（UDF），可在 SQL 表达式中调用。
     * 基于 H2 的 CREATE ALIAS 机制。
     *
     * <p>示例：
     * <pre>
     * engine.registerFunction("mask_phone", MyFuncs.class, "maskPhone");
     * df.withColumn("masked", "mask_phone(phone)")
     * </pre>
     *
     * @param name 函数名（SQL 中调用的名称）
     * @param clazz 包含静态方法的类
     * @param methodName 静态方法名
     */
    public void registerFunction(String name, Class<?> clazz, String methodName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function name must not be blank");
        }
        Objects.requireNonNull(clazz, "clazz must not be null");
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName must not be blank");
        }
        // 函数名白名单校验（防注入）
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("非法函数名: " + name);
        }
        try (Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE ALIAS IF NOT EXISTS " + name +
                    " FOR \"" + clazz.getName() + "." + methodName + "\"");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("注册 UDF 失败: " + name + ", " + e.getMessage(), e);
        }
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
                sb.append(col.getColumnName()).append(" ").append(toCanonicalDdlType(col.getDataTypeName()));
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
                            ColumnInfo col = df.getColumns().get(i);
                            Object val = row.getValue(col.getColumnName());
                            ps.setObject(i + 1, toH2Value(val, col.getDataTypeName()));
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
     * 透视表：行转列。使用 CASE WHEN + 聚合实现。
     */
    /** 包内可见：供 JdbcExecutionEngine 做"上游下推 + 内存收尾"混合执行时复用。 */
    RowDataFrame pivotData(RowDataFrame input,
            com.pl.gdl.dataframe.operator.base.PivotOperator pivotOp) {
        if (input.rowSize() == 0) {
            return new RowDataFrame();
        }
        String tableName = "__pivot_" + System.nanoTime();
        registerTable(tableName, input);
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            // 获取透视列的唯一值
            java.util.List<String> pivotValues = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = stmt.executeQuery(
                    "SELECT DISTINCT " + pivotOp.getPivotColumn() + " FROM " + tableName +
                    " WHERE " + pivotOp.getPivotColumn() + " IS NOT NULL ORDER BY 1")) {
                while (rs.next()) {
                    pivotValues.add(rs.getString(1));
                }
            }

            // 构建透视 SQL：别名直接使用原值加双引号（支持中文等非 ASCII），
            // 冲突时在引号内追加序号消解；内嵌双引号按 SQL 标准转义为 ""。
            StringBuilder sql = new StringBuilder("SELECT ");
            java.util.List<String> groupCols = pivotOp.getGroupByColumns();
            if (!groupCols.isEmpty()) {
                sql.append(String.join(", ", groupCols)).append(", ");
            }
            java.util.Set<String> usedAliases = new java.util.HashSet<>();
            for (int i = 0; i < pivotValues.size(); i++) {
                if (i > 0) sql.append(", ");
                String pv = pivotValues.get(i).replace("'", "''");
                String quoted = pivotValues.get(i).replace("\"", "\"\"");
                String alias = quoted;
                int suffix = 2;
                while (!usedAliases.add(alias)) {
                    alias = quoted + "_" + (suffix++);
                }
                sql.append(pivotOp.getAggFunction())
                   .append("(CASE WHEN ").append(pivotOp.getPivotColumn())
                   .append(" = '").append(pv).append("' THEN ")
                   .append(pivotOp.getValueColumn()).append(" END) AS \"").append(alias).append("\"");
            }
            sql.append(" FROM ").append(tableName);
            if (!groupCols.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupCols));
            }

            try (java.sql.ResultSet rs = stmt.executeQuery(sql.toString())) {
                java.sql.ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                java.util.List<com.pl.gdl.common.model.ColumnInfo> columns = new java.util.ArrayList<>();
                for (int i = 1; i <= cols; i++) {
                    columns.add(new com.pl.gdl.common.model.ColumnInfo(
                            md.getColumnLabel(i), md.getColumnTypeName(i)));
                }
                RowDataFrame result = new RowDataFrame(columns);
                while (rs.next()) {
                    com.pl.gdl.common.model.Row row = new com.pl.gdl.common.model.Row();
                    for (int i = 1; i <= cols; i++) {
                        row.setValue(md.getColumnLabel(i), rs.getObject(i));
                    }
                    result.addRow(row);
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("透视表执行失败: " + e.getMessage(), e);
        } finally {
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            } catch (Exception ignored) {
            }
            inMemoryTables.remove(tableName.toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * 数据探查：为每列生成统计信息行。
     */
    /** 包内可见：供 JdbcExecutionEngine 做"上游下推 + 内存收尾"混合执行时复用。 */
    RowDataFrame describeData(RowDataFrame input) {
        java.util.List<com.pl.gdl.common.model.ColumnInfo> outCols = java.util.List.of(
                new com.pl.gdl.common.model.ColumnInfo("column_name", "STRING"),
                new com.pl.gdl.common.model.ColumnInfo("data_type", "STRING"),
                new com.pl.gdl.common.model.ColumnInfo("row_count", "BIGINT"),
                new com.pl.gdl.common.model.ColumnInfo("null_count", "BIGINT"),
                new com.pl.gdl.common.model.ColumnInfo("distinct_count", "BIGINT"),
                new com.pl.gdl.common.model.ColumnInfo("min_value", "STRING"),
                new com.pl.gdl.common.model.ColumnInfo("max_value", "STRING"),
                new com.pl.gdl.common.model.ColumnInfo("avg_value", "STRING"));
        RowDataFrame output = new RowDataFrame(outCols);

        String tableName = "__describe_" + System.nanoTime();
        registerTable(tableName, input);
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            for (com.pl.gdl.common.model.ColumnInfo col : input.getColumns()) {
                String cn = col.getColumnName();
                String sql = "SELECT COUNT(*), COUNT(" + cn + "), COUNT(DISTINCT " + cn + "), " +
                        "MIN(CAST(" + cn + " AS VARCHAR)), MAX(CAST(" + cn + " AS VARCHAR)) FROM " + tableName;
                String avgSql = "SELECT AVG(CAST(" + cn + " AS DOUBLE)) FROM " + tableName +
                        " WHERE " + cn + " IS NOT NULL";
                try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        long rowCount = rs.getLong(1);
                        long nonNull = rs.getLong(2);
                        long distinct = rs.getLong(3);
                        String min = rs.getString(4);
                        String max = rs.getString(5);
                        String avg = null;
                        try (java.sql.ResultSet rs2 = stmt.executeQuery(avgSql)) {
                            if (rs2.next()) {
                                avg = rs2.getString(1);
                            }
                        } catch (Exception ignored) {
                            // 非数值列 AVG 会失败，忽略
                        }
                        com.pl.gdl.common.model.Row row = new com.pl.gdl.common.model.Row();
                        row.setValue("column_name", cn);
                        row.setValue("data_type", col.getDataTypeName());
                        row.setValue("row_count", rowCount);
                        row.setValue("null_count", rowCount - nonNull);
                        row.setValue("distinct_count", distinct);
                        row.setValue("min_value", min);
                        row.setValue("max_value", max);
                        row.setValue("avg_value", avg);
                        output.addRow(row);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("数据探查执行失败: " + e.getMessage(), e);
        } finally {
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            } catch (Exception ignored) {
            }
            inMemoryTables.remove(tableName.toLowerCase(java.util.Locale.ROOT));
        }
        return output;
    }

    /**
     * 数据质量检查：在内存表中执行条件查询，统计不满足条件的行数。
     * 使用 H2 SQL 的 NOT (condition) 来找出违规行。
     */
    /** 包内可见：供 JdbcExecutionEngine 做链中 validate 物化时复用。 */
    void validateDataQuality(RowDataFrame input,
            com.pl.gdl.dataframe.operator.base.ValidateOperator validateOp) {
        if (input.rowSize() == 0) {
            return;
        }
        String tableName = "__validate_" + System.nanoTime();
        registerTable(tableName, input);
        try {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE NOT (" + validateOp.getCondition() + ")";
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    long violations = rs.getLong(1);
                    if (violations > 0) {
                        throw new com.pl.gdl.dataframe.operator.base.DataQualityException(
                                validateOp.getMessage(), violations);
                    }
                }
            } catch (com.pl.gdl.dataframe.operator.base.DataQualityException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("数据质量检查执行失败: " + e.getMessage(), e);
            }
        } finally {
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + tableName);
            } catch (Exception ignored) {
            }
            inMemoryTables.remove(tableName.toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * 将 ColumnInfo 的数据类型名映射为 H2 SQL 类型。
     * 未知类型默认 VARCHAR(500)，保证兼容性。
     */
    /**
     * 将列类型名规范化为各数据源通用的 DDL 类型（VARCHAR(500)/BIGINT/DOUBLE 等，
     * 在 H2/SQLite/MySQL/PostgreSQL/Hive 均合法）。
     * <p>包内可见：供 JdbcExecutionEngine 物化临时表时复用。 */
    static String toCanonicalDdlType(String dataTypeName) {
        if (dataTypeName == null) {
            return "VARCHAR(500)";
        }
        String t = dataTypeName.trim().toUpperCase(java.util.Locale.ROOT);
        // 支持 VARCHAR(n) / CHAR(n) 保留长度
        if (t.startsWith("VARCHAR(") && t.endsWith(")")) {
            return t;
        }
        if (t.startsWith("CHAR(") && t.endsWith(")")) {
            return t;
        }
        switch (t) {
            case "INT":
            case "INTEGER":
            case "INT4":
            case "SERIAL":
                return "INTEGER";
            case "SMALLINT":
            case "SHORT":
            case "INT2":
                return "SMALLINT";
            case "TINYINT":
            case "BYTE":
                return "TINYINT";
            case "BIGINT":
            case "LONG":
            case "INT8":
            case "BIGSERIAL":
                return "BIGINT";
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT":
            case "FLOAT8":
            case "FLOAT4":
                return "DOUBLE";
            case "REAL":
                return "REAL";
            case "DECIMAL":
            case "NUMERIC":
                return "DECIMAL(38,10)";
            case "BOOLEAN":
            case "BOOL":
                return "BOOLEAN";
            case "DATE":
                return "DATE";
            case "TIME":
                return "TIME";
            case "TIMESTAMP":
            case "DATETIME":
                return "TIMESTAMP";
            case "CHAR":
                return "CHAR(1)";
            case "CHARACTER":
                return "CHAR(1)";
            case "STRING":
            case "TEXT":
            case "VARCHAR":
            case "CHARACTER VARYING":
            case "NVARCHAR":
            case "string":
            case "text":
            case "varchar":
                return "VARCHAR(500)";
            case "long":
                return "BIGINT";
            case "int":
            case "integer":
                return "INTEGER";
            case "double":
            case "float":
                return "DOUBLE";
            case "boolean":
                return "BOOLEAN";
            default:
                return "VARCHAR(500)";
        }
    }

    /**
     * 将值转换为适合 H2 插入的 Java 类型。数值类型尝试解析字符串，
     * 解析失败则回退为原值（H2 会尝试隐式转换）。
     */
    private static Object toH2Value(Object val, String dataTypeName) {
        if (val == null) {
            return null;
        }
        if (dataTypeName == null) {
            return val;
        }
        String type = dataTypeName.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            switch (type) {
                case "INT":
                case "INTEGER":
                case "INT4":
                case "INT2":
                case "SERIAL":
                    if (val instanceof Number) {
                        return ((Number) val).intValue();
                    }
                    return Integer.parseInt(val.toString().trim());
                case "BIGINT":
                case "LONG":
                case "INT8":
                case "BIGSERIAL":
                    if (val instanceof Number) {
                        return ((Number) val).longValue();
                    }
                    return Long.parseLong(val.toString().trim());
                case "DOUBLE":
                case "DOUBLE PRECISION":
                case "FLOAT":
                case "FLOAT8":
                case "FLOAT4":
                case "REAL":
                    if (val instanceof Number) {
                        return ((Number) val).doubleValue();
                    }
                    return Double.parseDouble(val.toString().trim());
                case "DECIMAL":
                case "NUMERIC":
                    if (val instanceof java.math.BigDecimal) {
                        return val;
                    }
                    return new java.math.BigDecimal(val.toString().trim());
                case "BOOLEAN":
                case "BOOL":
                    if (val instanceof Boolean) {
                        return val;
                    }
                    return Boolean.parseBoolean(val.toString().trim());
                default:
                    return val;
            }
        } catch (NumberFormatException e) {
            // 解析失败时回退为原值，让 H2 尝试处理
            return val;
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
        // dry-run（纯规划）模式：不触碰数据源（含内部 H2），直接返回空结果，杜绝任何副作用
        if (DryRun.isActive()) return new RowDataFrame();
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

        if (operator instanceof com.pl.gdl.dataframe.operator.base.ValidateOperator validateOp) {
            RowDataFrame input = !validateOp.getUpstream().isEmpty() ? execute(validateOp.getUpstream().get(0)) : new RowDataFrame();
            validateDataQuality(input, validateOp);
            return input;
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.base.DescribeOperator describeOp) {
            RowDataFrame input = !describeOp.getUpstream().isEmpty() ? execute(describeOp.getUpstream().get(0)) : new RowDataFrame();
            return describeData(input);
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.base.PivotOperator pivotOp) {
            RowDataFrame input = !pivotOp.getUpstream().isEmpty() ? execute(pivotOp.getUpstream().get(0)) : new RowDataFrame();
            return pivotData(input, pivotOp);
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.realtime.TumbleWindowOperator tumbleOp) {
            RowDataFrame input = !tumbleOp.getUpstream().isEmpty() ? execute(tumbleOp.getUpstream().get(0)) : new RowDataFrame();
            return applyTumbleWindow(input, tumbleOp);
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.realtime.HopWindowOperator hopOp) {
            RowDataFrame input = !hopOp.getUpstream().isEmpty() ? execute(hopOp.getUpstream().get(0)) : new RowDataFrame();
            return applyHopWindow(input, hopOp);
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.realtime.CumulateWindowOperator cumOp) {
            RowDataFrame input = !cumOp.getUpstream().isEmpty() ? execute(cumOp.getUpstream().get(0)) : new RowDataFrame();
            return applyCumulateWindow(input, cumOp);
        }

        if (operator instanceof com.pl.gdl.dataframe.operator.advanced.HttpOperator httpOp) {
            RowDataFrame input = !httpOp.getUpstream().isEmpty() ? execute(httpOp.getUpstream().get(0)) : new RowDataFrame();
            return new com.pl.gdl.dataframe.http.HttpCallExecutor().execute(httpOp, input);
        }

        if (operator instanceof FromOperator fromOp) {
            String tbl = fromOp.getTableName().toLowerCase(Locale.ROOT);
            if (inMemoryTables.containsKey(tbl)) {
                return inMemoryTables.get(tbl);
            }
        }

        // 若算子树含窗口算子（内存实现），先物化为临时表再走 SQL
        operator = materializeWindows(operator);

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

    /** 递归查找窗口算子，内存执行并替换为临时表 From，返回新算子树 */
    private LogicalOperator materializeWindows(LogicalOperator op) {
        if (op instanceof com.pl.gdl.dataframe.operator.realtime.TumbleWindowOperator
                || op instanceof com.pl.gdl.dataframe.operator.realtime.HopWindowOperator
                || op instanceof com.pl.gdl.dataframe.operator.realtime.CumulateWindowOperator) {
            RowDataFrame windowed = execute(op);
            String tmpTable = "__window_" + System.nanoTime();
            registerTable(tmpTable, windowed);
            return new com.pl.gdl.dataframe.operator.base.FromOperator(null, tmpTable);
        }
        java.util.List<LogicalOperator> ups = op.getUpstream();
        if (ups == null || ups.isEmpty()) return op;
        boolean changed = false;
        java.util.List<LogicalOperator> newUps = new java.util.ArrayList<>();
        for (LogicalOperator u : ups) {
            LogicalOperator nu = materializeWindows(u);
            newUps.add(nu);
            if (nu != u) changed = true;
        }
        if (!changed) return op;
        // 用反射替换 upstream（LogicalOperator 的 upstream 可变）
        try {
            java.lang.reflect.Field f = LogicalOperator.class.getDeclaredField("upstream");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<LogicalOperator> list = (java.util.List<LogicalOperator>) f.get(op);
            list.clear();
            list.addAll(newUps);
        } catch (Exception e) {
            throw new RuntimeException("替换窗口算子失败", e);
        }
        return op;
    }

    // ================= 实时窗口（内存实现，全数据源兼容） =================

    /** 时间单位转毫秒 */
    private static long toMillis(String amount, String unit) {
        long n = Long.parseLong(amount.trim());
        String u = unit.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (u) {
            case "MILLISECONDS", "MILLISECOND", "MS" -> n;
            case "SECONDS", "SECOND", "S" -> n * 1000L;
            case "MINUTES", "MINUTE", "M" -> n * 60_000L;
            case "HOURS", "HOUR", "H" -> n * 3_600_000L;
            case "DAYS", "DAY", "D" -> n * 86_400_000L;
            default -> throw new IllegalArgumentException("不支持的时间单位: " + unit);
        };
    }

    /** 解析时间列值为 epoch millis */
    private static long parseTime(Object v) {
        if (v == null) throw new IllegalArgumentException("窗口时间列含 null");
        if (v instanceof Number num) {
            long l = num.longValue();
            // 10 位=秒，13 位=毫秒
            return String.valueOf(Math.abs(l)).length() <= 10 ? l * 1000L : l;
        }
        String s = String.valueOf(v).trim();
        // 去掉毫秒小数部分（如 "2026-10-08 10:00:00.0"）
        s = s.replaceAll("\\.\\d+$", "");
        // 尝试 ISO / yyyy-MM-dd HH:mm:ss
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {}
        for (String fmt : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"}) {
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(s,
                        java.time.format.DateTimeFormatter.ofPattern(fmt));
                return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {}
            try {
                java.time.LocalDate ld = java.time.LocalDate.parse(s,
                        java.time.format.DateTimeFormatter.ofPattern(fmt));
                return ld.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {}
        }
        // 纯数字字符串
        try {
            long l = Long.parseLong(s);
            return String.valueOf(Math.abs(l)).length() <= 10 ? l * 1000L : l;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析时间值: " + s);
        }
    }

    /** 构造窗口输出：原列 + window_start/window_end */
    private static RowDataFrame buildWindowed(RowDataFrame input,
                                              java.util.List<com.pl.gdl.common.model.Row> outRows,
                                              java.util.List<long[]> windows) {
        java.util.List<com.pl.gdl.common.model.ColumnInfo> inCols = input.getColumns();
        java.util.List<com.pl.gdl.common.model.ColumnInfo> outCols = new java.util.ArrayList<>(inCols);
        outCols.add(new com.pl.gdl.common.model.ColumnInfo("window_start", "timestamp"));
        outCols.add(new com.pl.gdl.common.model.ColumnInfo("window_end", "timestamp"));
        RowDataFrame out = new RowDataFrame(outCols);
        for (int i = 0; i < outRows.size(); i++) {
            com.pl.gdl.common.model.Row r = outRows.get(i);
            long[] w = windows.get(i);
            java.util.Map<String, Object> vals = new java.util.LinkedHashMap<>();
            for (com.pl.gdl.common.model.ColumnInfo c : inCols) {
                vals.put(c.getColumnName(), r.getValue(c.getColumnName()));
            }
            vals.put("window_start", new java.sql.Timestamp(w[0]));
            vals.put("window_end", new java.sql.Timestamp(w[1]));
            out.addRow(new com.pl.gdl.common.model.Row(vals));
        }
        return out;
    }

    /** 滚动窗口：size 固定、无重叠 */
    private RowDataFrame applyTumbleWindow(RowDataFrame input,
            com.pl.gdl.dataframe.operator.realtime.TumbleWindowOperator op) {
        long sizeMs = toMillis(op.getSize(), op.getUnit());
        long offsetMs = (op.getOffset() != null && !op.getOffset().isBlank())
                ? toMillis(op.getOffset(), op.getOffsetUnit()) : 0L;
        String timeCol = op.getTimeColumn();
        java.util.List<com.pl.gdl.common.model.Row> outRows = new java.util.ArrayList<>();
        java.util.List<long[]> windows = new java.util.ArrayList<>();
        for (com.pl.gdl.common.model.Row r : input.getRows()) {
            long t = parseTime(r.getValue(timeCol));
            long wStart = ((t - offsetMs) / sizeMs) * sizeMs + offsetMs;
            // 负时间戳向下取整修正
            if (wStart > t) wStart -= sizeMs;
            outRows.add(r);
            windows.add(new long[]{wStart, wStart + sizeMs});
        }
        return buildWindowed(input, outRows, windows);
    }

    /** 滑动窗口：size 固定、按 slide 步长滑动，一行属多窗 */
    private RowDataFrame applyHopWindow(RowDataFrame input,
            com.pl.gdl.dataframe.operator.realtime.HopWindowOperator op) {
        long slideMs = toMillis(op.getSlideTime(), op.getSlideUnit());
        long sizeMs = toMillis(op.getWindowSize(), op.getWindowUnit());
        long offsetMs = (op.getOffset() != null && !op.getOffset().isBlank())
                ? toMillis(op.getOffset(), op.getOffsetUnit()) : 0L;
        if (slideMs <= 0 || sizeMs <= 0) throw new IllegalArgumentException("窗口大小/步长必须为正");
        String timeCol = op.getTimeColumn();
        java.util.List<com.pl.gdl.common.model.Row> outRows = new java.util.ArrayList<>();
        java.util.List<long[]> windows = new java.util.ArrayList<>();
        for (com.pl.gdl.common.model.Row r : input.getRows()) {
            long t = parseTime(r.getValue(timeCol));
            // 包含 t 的窗口：wStart ∈ (t - size, t]，且 wStart 与 offset 对齐到 slide
            long first = t - sizeMs + 1;
            long wStart = ((first - offsetMs) / slideMs) * slideMs + offsetMs;
            if (wStart < first) wStart += slideMs;
            // 负时间戳修正
            while (wStart + slideMs <= first) wStart += slideMs;
            while (wStart - slideMs >= first - slideMs && wStart - slideMs + sizeMs > t) {
                // 保持最小的 wStart
                long cand = wStart - slideMs;
                if (cand + sizeMs > t && cand <= t) wStart = cand; else break;
            }
            for (long ws = wStart; ws <= t; ws += slideMs) {
                if (ws + sizeMs > t) {
                    outRows.add(r);
                    windows.add(new long[]{ws, ws + sizeMs});
                }
            }
        }
        return buildWindowed(input, outRows, windows);
    }

    /** 累积窗口：从起点按 step 增长到 size，如 [00:00-00:05),[00:00-00:10)... */
    private RowDataFrame applyCumulateWindow(RowDataFrame input,
            com.pl.gdl.dataframe.operator.realtime.CumulateWindowOperator op) {
        long stepMs = toMillis(op.getStepTime(), op.getStepUnit());
        long sizeMs = toMillis(op.getWindowSize(), op.getWindowUnit());
        String timeCol = op.getTimeColumn();
        java.util.List<com.pl.gdl.common.model.Row> outRows = new java.util.ArrayList<>();
        java.util.List<long[]> windows = new java.util.ArrayList<>();
        for (com.pl.gdl.common.model.Row r : input.getRows()) {
            long t = parseTime(r.getValue(timeCol));
            long base = (t / sizeMs) * sizeMs;
            if (base > t) base -= sizeMs;
            // 该行属于 [base, base+k*step)，k=1..size/step，且 base+k*step > t
            for (long k = 1; k * stepMs <= sizeMs; k++) {
                long wEnd = base + k * stepMs;
                if (wEnd > t) {
                    outRows.add(r);
                    windows.add(new long[]{base, wEnd});
                }
            }
        }
        return buildWindowed(input, outRows, windows);
    }
}
