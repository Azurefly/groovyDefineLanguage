package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class PythonScriptOperator extends LogicalOperator {
    private final CmdDatasource datasource;
    private final String outputTable;
    private final String scriptContent;

    public PythonScriptOperator(CmdDatasource datasource, String outputTable, String scriptContent) {
        this.datasource = datasource;
        this.outputTable = outputTable;
        this.scriptContent = scriptContent;
        if (datasource != null) {
            this.areaCode = datasource.getAreaCode();
        }
    }

    public CmdDatasource getDatasource() { return datasource; }
    public String getOutputTable() { return outputTable; }
    public String getScriptContent() { return scriptContent; }

    @Override
    public String getOperatorName() {
        return "python";
    }

    @Override
    public String toString() {
        return "python(" + outputTable + ")";
    }
}
