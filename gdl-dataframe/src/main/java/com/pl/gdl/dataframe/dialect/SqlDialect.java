package com.pl.gdl.dataframe.dialect;

public interface SqlDialect {
    /**
     * 返回方言名称（如 H2、HIVE、MYSQL）。
     */
    String getDialectName();

    /**
     * 引用标识符（表名/列名），内部引号字符按该方言规则 doubling 转义。
     */
    String quoteIdentifier(String name);

    /**
     * 生成分页子句（如 LIMIT/OFFSET 或 LIMIT offset, limit）。
     */
    String formatLimit(int offset, int limit);

    /**
     * 返回该方言的随机函数名（不带括号），如 H2/MySQL/Hive 的 RAND，Postgres 的 RANDOM。
     * seed 为 null 时不带参数，否则带 seed 参数。
     */
    default String formatRandom(Long seed) {
        return seed != null ? "RAND(" + seed + ")" : "RAND()";
    }

    /**
     * 该方言的随机函数是否支持 seed 参数（如 H2/MySQL 的 RAND(seed)）。
     * 不支持时（如 SQLite 的 RANDOM()），带 seed 的采样走内存确定性采样。
     */
    default boolean supportsSeededRandom() {
        return true;
    }

    /**
     * 生成"分组内按排序取首行"的 SQL（通常基于 ROW_NUMBER 窗口函数）。
     */
    String formatGroupSortFirst(String groupCols, String sortCols, String sourceTable);

    /**
     * 生成按分隔符拼接聚合的表达式（如 concat_ws / string_agg / GROUP_CONCAT）。
     */
    String formatConcatWs(String delimiter, String expression);

    /**
     * 生成"覆盖写整表"的 SQL（先清空再插入查询结果）。
     */
    String formatOverwriteTable(String tableName, String selectSql);

    /**
     * 生成"覆盖写指定分区"的 SQL（先删除分区数据再插入查询结果）。
     */
    String formatOverwritePartition(String tableName, String partitionSpec, String selectSql);

    /**
     * 日期加减：dateExpr + n 个单位（如 DATE_ADD(dateExpr, INTERVAL n DAY)）。
     * unit 取值为 DAY/MONTH/YEAR/HOUR/MINUTE/SECOND。
     */
    default String formatDateAdd(String dateExpr, int amount, String unit) {
        return "DATEADD('" + unit + "', " + amount + ", " + dateExpr + ")";
    }

    /**
     * 日期差值：返回 endDate - startDate 的单位数。
     * unit 取值为 DAY/MONTH/YEAR/HOUR/MINUTE/SECOND。
     */
    default String formatDateDiff(String unit, String startDate, String endDate) {
        return "DATEDIFF('" + unit + "', " + startDate + ", " + endDate + ")";
    }

    /**
     * 日期格式化为字符串（如 yyyy-MM-dd）。
     */
    default String formatDateFormat(String dateExpr, String pattern) {
        return "FORMATDATETIME(" + dateExpr + ", '" + pattern + "')";
    }

    /**
     * 当前日期时间。
     */
    default String formatCurrentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }
}
