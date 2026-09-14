package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.LinkedHashMap;
import java.util.Map;

public class LlmCallOperator extends LogicalOperator {
    private final CmdDatasource llmDatasource;
    private final String modelName;
    private final String role;
    private final String target;
    private final String resultColumn;
    private final Map<String, Object> modelParams;

    public LlmCallOperator(LogicalOperator upstream, CmdDatasource llmDatasource, String modelName,
                           String role, String target, String resultColumn, Map<String, Object> modelParams) {
        addUpstream(upstream);
        this.llmDatasource = llmDatasource;
        this.modelName = modelName;
        this.role = role != null ? role : "";
        this.target = target;
        this.resultColumn = resultColumn;
        this.modelParams = modelParams != null ? new LinkedHashMap<>(modelParams) : new LinkedHashMap<>();
    }

    public CmdDatasource getLlmDatasource() { return llmDatasource; }
    public String getModelName() { return modelName; }
    public String getRole() { return role; }
    public String getTarget() { return target; }
    public String getResultColumn() { return resultColumn; }
    public Map<String, Object> getModelParams() { return modelParams; }

    @Override
    public String getOperatorName() {
        return "llmCall";
    }

    @Override
    public String toString() {
        return "llmCall(" + modelName + ", role='" + role + "', resultCol='" + resultColumn + "')";
    }
}
