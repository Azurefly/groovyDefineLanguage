package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

/**
 * @deprecated Python 脚本执行尚未实现，当前版本不可用。
 * 如需自定义逻辑，请使用 {@code groovy(Closure)}。
 * 本类将在未来版本中移除或实现。
 */
@Deprecated
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
