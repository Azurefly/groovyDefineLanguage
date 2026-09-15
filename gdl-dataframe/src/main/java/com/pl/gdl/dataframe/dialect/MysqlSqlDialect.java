package com.pl.gdl.dataframe.dialect;

public class MysqlSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "MYSQL"; }
    @Override public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "`" + name.replace("`", "") + "`";
    }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + " SEPARATOR '" + delimiter + "')";
    }
}
