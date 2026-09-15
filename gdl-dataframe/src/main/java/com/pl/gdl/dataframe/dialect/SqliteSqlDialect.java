package com.pl.gdl.dataframe.dialect;

public class SqliteSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "SQLITE"; }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + ", '" + delimiter + "')";
    }
}
