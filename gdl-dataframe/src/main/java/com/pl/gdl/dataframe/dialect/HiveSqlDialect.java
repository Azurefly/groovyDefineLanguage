package com.pl.gdl.dataframe.dialect;

public class HiveSqlDialect implements SqlDialect {
    @Override
    public String getDialectName() {
        return "HIVE";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "`" + name.replace("`", "") + "`";
    }

    @Override
    public String formatLimit(int offset, int limit) {
        if (offset > 0) {
            return "LIMIT " + offset + ", " + limit;
        }
        return "LIMIT " + limit;
    }

    @Override
    public String formatGroupSortFirst(String groupCols, String sortCols, String sourceTable) {
        return "SELECT * FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY " + groupCols + " ORDER BY " + sortCols + ") AS rn FROM " + sourceTable + ") t WHERE t.rn = 1";
    }

    @Override
    public String formatConcatWs(String delimiter, String expression) {
        return "concat_ws('" + delimiter + "', sort_array(collect_list(" + expression + ")))";
    }

    @Override
    public String formatOverwriteTable(String tableName, String selectSql) {
        return "INSERT OVERWRITE TABLE " + tableName + " " + selectSql;
    }

    @Override
    public String formatOverwritePartition(String tableName, String partitionSpec, String selectSql) {
        return "INSERT OVERWRITE TABLE " + tableName + " PARTITION (" + partitionSpec + ") " + selectSql;
    }
}
