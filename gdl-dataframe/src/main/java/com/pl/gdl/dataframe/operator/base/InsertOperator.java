package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class InsertOperator extends LogicalOperator {
    private final CmdDatasource datasource;
    private final String targetTable;
    private final String insertSql;

    public InsertOperator(CmdDatasource datasource, String targetTable, String insertSql) {
        this.datasource = datasource;
        this.targetTable = targetTable;
        this.insertSql = insertSql;
        if (datasource != null) {
            this.areaCode = datasource.getAreaCode();
        }
    }

    public CmdDatasource getDatasource() { return datasource; }
    public String getTargetTable() { return targetTable; }
    public String getInsertSql() { return insertSql; }

    @Override
    public String getOperatorName() {
        return "insert";
    }

    @Override
    public String toString() {
        return "insert(" + targetTable + ", " + insertSql + ")";
    }
}
