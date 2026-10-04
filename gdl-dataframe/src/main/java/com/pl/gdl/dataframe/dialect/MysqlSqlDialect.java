package com.pl.gdl.dataframe.dialect;

public class MysqlSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "MYSQL"; }
    @Override public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "`" + name.replace("`", "``") + "`";
    }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + " SEPARATOR '" + escapeStringLiteral(delimiter) + "')";
    }
    @Override public String formatDateAdd(String dateExpr, int amount, String unit) {
        return "DATE_ADD(" + dateExpr + ", INTERVAL " + amount + " " + unit + ")";
    }
    @Override public String formatDateDiff(String unit, String startDate, String endDate) {
        // MySQL DATEDIFF 只返回天数且参数顺序为 (end, start)
        if ("DAY".equalsIgnoreCase(unit)) {
            return "DATEDIFF(" + endDate + ", " + startDate + ")";
        }
        return "TIMESTAMPDIFF(" + unit + ", " + startDate + ", " + endDate + ")";
    }
    @Override public String formatDateFormat(String dateExpr, String pattern) {
        String mysqlPattern = pattern.replace("yyyy", "%Y").replace("MM", "%m").replace("dd", "%d")
                .replace("HH", "%H").replace("mm", "%i").replace("ss", "%s");
        return "DATE_FORMAT(" + dateExpr + ", '" + mysqlPattern + "')";
    }
}
