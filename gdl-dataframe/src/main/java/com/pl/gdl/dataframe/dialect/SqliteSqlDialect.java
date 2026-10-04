package com.pl.gdl.dataframe.dialect;

public class SqliteSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "SQLITE"; }
    /** SQLite 随机函数为 RANDOM()，不支持 seed 参数：带 seed 的采样由引擎走内存确定性采样。 */
    @Override public String formatRandom(Long seed) { return "RANDOM()"; }
    @Override public boolean supportsSeededRandom() { return false; }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + ", '" + escapeStringLiteral(delimiter) + "')";
    }
}
