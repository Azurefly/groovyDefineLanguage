package com.pl.gdl.dataframe.dialect;

public interface SqlDialect {
    String getDialectName();
    String quoteIdentifier(String name);
    String formatLimit(int offset, int limit);
    String formatGroupSortFirst(String groupCols, String sortCols, String sourceTable);
    String formatConcatWs(String delimiter, String expression);
    String formatOverwriteTable(String tableName, String selectSql);
    String formatOverwritePartition(String tableName, String partitionSpec, String selectSql);
}
