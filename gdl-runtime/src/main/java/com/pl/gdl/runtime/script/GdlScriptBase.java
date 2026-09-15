package com.pl.gdl.runtime.script;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import com.pl.gdl.dataframe.operator.advanced.PythonScriptOperator;
import com.pl.gdl.runtime.federation.FederatedJoinRequest;
import groovy.lang.Closure;
import groovy.lang.Script;

import java.util.List;
import java.util.Map;

public abstract class GdlScriptBase extends Script {

    protected GdlExecutionContext getContext() {
        return GdlExecutionContext.get();
    }

    /** Generic, plugin-friendly datasource constructor. */
    public CmdDatasource datasource(String type) {
        return datasource(type, Map.of());
    }

    public CmdDatasource datasource(String type, Map<String, Object> config) {
        return getContext().getDatasourceRegistry().create(type, config);
    }

    // Backward-compatible datasource helpers
    public CmdDatasource hive() { return new HiveDatasource(); }
    public CmdDatasource hive(String confName) { return new HiveDatasource(confName); }
    public CmdDatasource hivehw() { return hive(); }
    public CmdDatasource hivehw(String confName) { return hive(confName); }

    public CmdDatasource postgres(String host, int port, String db, String user, String pass) {
        return new PostgresDatasource(host, port, db, user, pass);
    }

    public CmdDatasource mysql(String host, int port, String db, String user, String pass) {
        return new MysqlDatasource(host, port, db, user, pass);
    }

    public CmdDatasource sqlite(String path) {
        return new SqliteDatasource(path);
    }

    public CmdDatasource h2() {
        return new H2Datasource();
    }

    public CmdDatasource llm() { return new LlmDatasource(); }
    public CmdDatasource llm(String url, int concurrent) { return new LlmDatasource(url, concurrent); }

    // Dataset extraction
    public CmdDataframe from(CmdDatasource ds, String table) { return getContext().createFrom(ds, table); }
    public CmdDataframe query(CmdDatasource ds, String sql) { return getContext().createQuery(ds, sql); }
    public CmdDataframe insert(CmdDatasource ds, String targetTable, String sql) { return getContext().createInsert(ds, targetTable, sql); }

    /**
     * First executable cross-datasource path. Source SQL is pushed down to
     * each provider and rows are joined through an in-memory exchange.
     */
    public RowDataFrame federatedJoin(CmdDatasource leftDatasource, String leftSql, String leftKey,
                                      CmdDatasource rightDatasource, String rightSql, String rightKey) {
        return federatedJoin(leftDatasource, leftSql, leftKey, "left",
                rightDatasource, rightSql, rightKey, "right", "INNER");
    }

    public RowDataFrame federatedJoin(CmdDatasource leftDatasource, String leftSql, String leftKey, String leftAlias,
                                      CmdDatasource rightDatasource, String rightSql, String rightKey, String rightAlias,
                                      String joinType) {
        FederatedJoinRequest.JoinType type = FederatedJoinRequest.JoinType.valueOf(
                joinType == null ? "INNER" : joinType.trim().toUpperCase());
        FederatedJoinRequest request = new FederatedJoinRequest(
                leftDatasource, leftSql, leftKey, leftAlias,
                rightDatasource, rightSql, rightKey, rightAlias, type);
        return getContext().executeFederatedJoin(request).rows();
    }

    // Realtime & Signal operators
    public CmdDataframe periodReactor(String cronExpr) { return getContext().createPeriodReactor(cronExpr); }
    public CmdDataframe taskReactor(List<String> taskIds, int successRate, int delaySec) {
        return getContext().createTaskReactor(taskIds, successRate, delaySec);
    }

    // Dynamic variables
    public Object variable(String generatorName) { return variable(generatorName, Map.of()); }
    public Object variable(String generatorName, Map<String, Object> params) {
        return getContext().resolveVariable(generatorName, params);
    }

    // Advanced operators
    public HttpOperator http(String method, String url) { return getContext().createHttp(method, url); }
    public CmdDataframe groovy(Closure<RowDataFrame> closure) { return getContext().createGroovy(closure); }

    public CmdDataframe python(CmdDatasource ds, String outputTable, String scriptContent) {
        PythonScriptOperator op = new PythonScriptOperator(ds, outputTable, scriptContent);
        return new com.pl.gdl.dataframe.dataframe.CmdDataframeImpl(op, getContext().getExecutionEngine());
    }

    // Drift operators
    public DriftToBuilder driftTo(String targetAreaCode) { return new DriftToBuilder(targetAreaCode); }
    public CmdDataframe driftFrom(CmdDatasource ds, String datasetName, String type) { return from(ds, datasetName); }

    // Annotations & Terminal Handlers
    public void comment(String text) { getContext().addComment(text); }
    public void returnDf(CmdDataframe df) { getContext().setReturnDf(df); }

    public static class DriftToBuilder {
        private final String targetAreaCode;
        public DriftToBuilder(String targetAreaCode) { this.targetAreaCode = targetAreaCode; }
        public DriftToBuilder attach(CmdDataframe df, String originalTable, String newTable, String type) { return this; }
    }
}
