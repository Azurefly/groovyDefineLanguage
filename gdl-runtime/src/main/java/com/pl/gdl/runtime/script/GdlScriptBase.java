package com.pl.gdl.runtime.script;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.datasource.LlmDatasource;
import com.pl.gdl.dataframe.datasource.PostgresDatasource;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import com.pl.gdl.dataframe.operator.advanced.PythonScriptOperator;
import groovy.lang.Closure;
import groovy.lang.Script;

import java.util.List;
import java.util.Map;

public abstract class GdlScriptBase extends Script {

    protected GdlExecutionContext getContext() {
        return GdlExecutionContext.get();
    }

    // Data source definitions
    public CmdDatasource hive() {
        return new HiveDatasource();
    }

    public CmdDatasource hive(String confName) {
        return new HiveDatasource(confName);
    }

    public CmdDatasource hivehw() {
        return hive();
    }

    public CmdDatasource hivehw(String confName) {
        return hive(confName);
    }

    public CmdDatasource postgres(String host, int port, String db, String user, String pass) {
        return new PostgresDatasource(host, port, db, user, pass);
    }

    public CmdDatasource llm() {
        return new LlmDatasource();
    }

    public CmdDatasource llm(String url, int concurrent) {
        return new LlmDatasource(url, concurrent);
    }

    // Dataset extraction
    public CmdDataframe from(CmdDatasource ds, String table) {
        return getContext().createFrom(ds, table);
    }

    public CmdDataframe query(CmdDatasource ds, String sql) {
        return getContext().createQuery(ds, sql);
    }

    public CmdDataframe insert(CmdDatasource ds, String targetTable, String sql) {
        return getContext().createInsert(ds, targetTable, sql);
    }

    // Realtime & Signal operators
    public CmdDataframe periodReactor(String cronExpr) {
        return getContext().createPeriodReactor(cronExpr);
    }

    public CmdDataframe taskReactor(List<String> taskIds, int successRate, int delaySec) {
        return getContext().createTaskReactor(taskIds, successRate, delaySec);
    }

    // Dynamic variables
    public Object variable(String generatorName) {
        return variable(generatorName, Map.of());
    }

    public Object variable(String generatorName, Map<String, Object> params) {
        return getContext().resolveVariable(generatorName, params);
    }

    // Advanced operators
    public HttpOperator http(String method, String url) {
        return getContext().createHttp(method, url);
    }

    public CmdDataframe groovy(Closure<RowDataFrame> closure) {
        return getContext().createGroovy(closure);
    }

    public CmdDataframe python(CmdDatasource ds, String outputTable, String scriptContent) {
        PythonScriptOperator op = new PythonScriptOperator(ds, outputTable, scriptContent);
        return new com.pl.gdl.dataframe.dataframe.CmdDataframeImpl(op, getContext().getExecutionEngine());
    }

    // Drift operators
    public DriftToBuilder driftTo(String targetAreaCode) {
        return new DriftToBuilder(targetAreaCode);
    }

    public CmdDataframe driftFrom(CmdDatasource ds, String datasetName, String type) {
        return from(ds, datasetName);
    }

    // Annotations & Terminal Handlers
    public void comment(String text) {
        getContext().addComment(text);
    }

    public void returnDf(CmdDataframe df) {
        getContext().setReturnDf(df);
    }

    public static class DriftToBuilder {
        private final String targetAreaCode;

        public DriftToBuilder(String targetAreaCode) {
            this.targetAreaCode = targetAreaCode;
        }

        public DriftToBuilder attach(CmdDataframe df, String originalTable, String newTable, String type) {
            return this;
        }
    }
}
