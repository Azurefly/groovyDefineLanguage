package com.pl.gdl.dataframe.dialect;

public class PostgresSqlDialect implements SqlDialect {
    @Override
    public String getDialectName() {
        return "POSTGRES";
    }

    @Override
    public String quoteIdentifier(String name) {
        if (name == null) return "";
        return "\"" + name.replace("\"", "") + "\"";
    }

    @Override
    public String formatLimit(int offset, int limit) {
        if (offset > 0) {
            return "LIMIT " + limit + " OFFSET " + offset;
        }
        return "LIMIT " + limit;
    }

    @Override
    public String formatGroupSortFirst(String groupCols, String sortCols, String sourceTable) {
        return "SELECT DISTINCT ON (" + groupCols + ") * FROM " + sourceTable + " ORDER BY " + groupCols + ", " + sortCols;
    }

    @Override
    public String formatConcatWs(String delimiter, String expression) {
        return "string_agg(" + expression + ", '" + delimiter + "')";
    }

    @Override
    public String formatOverwriteTable(String tableName, String selectSql) {
        return "TRUNCATE TABLE " + tableName + "; INSERT INTO " + tableName + " " + selectSql;
    }

    @Override
    public String formatOverwritePartition(String tableName, String partitionSpec, String selectSql) {
        return "DELETE FROM " + tableName + " WHERE " + partitionSpec + "; INSERT INTO " + tableName + " " + selectSql;
    }
}
