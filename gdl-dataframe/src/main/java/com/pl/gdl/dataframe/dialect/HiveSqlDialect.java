package com.pl.gdl.dataframe.dialect;

public class HiveSqlDialect implements SqlDialect {
    @Override
    public String getDialectName() {
        return "HIVE";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "`" + name.replace("`", "``") + "`";
    }

    @Override
    public String formatLimit(int offset, int limit) {
        if (offset > 0) {
            // HiveQL 不支持 LIMIT offset, limit 语法（仅支持 LIMIT n），明确抛异常而非生成错误 SQL
            throw new UnsupportedOperationException(
                    "Hive 方言不支持带 offset 的 LIMIT（offset=" + offset + "），请使用 limit(n) 或先过滤再分页");
        }
        return "LIMIT " + limit;
    }

    @Override
    public String formatGroupSortFirst(String groupCols, String sortCols, String sourceTable) {
        return "SELECT * FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY " + groupCols + " ORDER BY " + sortCols + ") AS rn FROM " + sourceTable + ") t WHERE t.rn = 1";
    }

    @Override
    public String formatConcatWs(String delimiter, String expression) {
        return "concat_ws('" + escapeStringLiteral(delimiter) + "', sort_array(collect_list(" + expression + ")))";
    }

    /**
     * SQL 字符串字面量转义：单引号 doubling。
     */
    private static String escapeStringLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    @Override
    public String formatOverwriteTable(String tableName, String selectSql) {
        return "INSERT OVERWRITE TABLE " + tableName + " " + selectSql;
    }

    @Override
    public String formatOverwritePartition(String tableName, String partitionSpec, String selectSql) {
        return "INSERT OVERWRITE TABLE " + tableName + " PARTITION (" + partitionSpec + ") " + selectSql;
    }

    @Override
    public String formatDateAdd(String dateExpr, int amount, String unit) {
        // Hive DATE_ADD 只支持天；其他单位用 INTERVAL 表达式
        if ("DAY".equalsIgnoreCase(unit)) {
            return "DATE_ADD(" + dateExpr + ", " + amount + ")";
        }
        return "(" + dateExpr + " + INTERVAL " + amount + " " + unit + "S)";
    }

    @Override
    public String formatDateDiff(String unit, String startDate, String endDate) {
        // Hive DATEDIFF 只返回天数
        return "DATEDIFF(" + endDate + ", " + startDate + ")";
    }

    @Override
    public String formatDateFormat(String dateExpr, String pattern) {
        return "DATE_FORMAT(" + dateExpr + ", '" + pattern + "')";
    }
}
