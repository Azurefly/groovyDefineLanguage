package com.pl.gdl.dataframe.dialect;

public class SqliteSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "SQLITE"; }
    /** SQLite 随机函数为 RANDOM()，且不支持 seed 参数（seed 会被忽略）。 */
    @Override public String formatRandom(Long seed) { return "RANDOM()"; }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + ", '" + escapeStringLiteral(delimiter) + "')";
    }
}
