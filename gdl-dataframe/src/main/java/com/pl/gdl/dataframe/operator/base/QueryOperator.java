package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class QueryOperator extends LogicalOperator {
    private final CmdDatasource datasource;
    private final String querySql;

    public QueryOperator(CmdDatasource datasource, String querySql) {
        this.datasource = datasource;
        this.querySql = querySql;
        if (datasource != null) {
            this.areaCode = datasource.getAreaCode();
        }
    }

    public CmdDatasource getDatasource() { return datasource; }
    public String getQuerySql() { return querySql; }

    @Override
    public String getOperatorName() {
        return "query";
    }

    @Override
    public String toString() {
        return "query(" + querySql + ")";
    }
}
