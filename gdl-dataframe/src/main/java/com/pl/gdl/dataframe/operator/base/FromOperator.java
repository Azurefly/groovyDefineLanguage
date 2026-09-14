package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class FromOperator extends LogicalOperator {
    private final CmdDatasource datasource;
    private final String tableName;

    public FromOperator(CmdDatasource datasource, String tableName) {
        this.datasource = datasource;
        this.tableName = tableName;
        if (datasource != null) {
            this.areaCode = datasource.getAreaCode();
        }
    }

    public CmdDatasource getDatasource() { return datasource; }
    public String getTableName() { return tableName; }

    @Override
    public String getOperatorName() {
        return "from";
    }

    @Override
    public String toString() {
        return "from " + (datasource != null ? datasource.getDatasourceType() + ":" : "") + tableName;
    }
}
