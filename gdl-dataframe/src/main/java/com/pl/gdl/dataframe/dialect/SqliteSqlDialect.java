package com.pl.gdl.dataframe.dialect;

public class SqliteSqlDialect extends H2SqlDialect {
    @Override public String getDialectName() { return "SQLITE"; }
    /** SQLite 随机函数为 RANDOM()，不支持 seed 参数：带 seed 的采样由引擎走内存确定性采样。 */
    @Override public String formatRandom(Long seed) { return "RANDOM()"; }
    @Override public boolean supportsSeededRandom() { return false; }
    /** SQLite 不接受 (SELECT...) UNION (SELECT...) 的分支括号，改无括号形式。 */
    @Override public String formatSetOperation(String left, String keyword, String right) {
        return left + " " + keyword + " " + right;
    }
    @Override public String formatConcatWs(String delimiter, String expression) {
        return "GROUP_CONCAT(" + expression + ", '" + escapeStringLiteral(delimiter) + "')";
    }
}
