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
}
