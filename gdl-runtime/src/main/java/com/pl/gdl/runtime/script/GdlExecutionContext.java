package com.pl.gdl.runtime.script;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.advanced.GroovyCustomOperator;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.dataframe.operator.base.InsertOperator;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.dataframe.operator.realtime.PeriodReactorOperator;
import com.pl.gdl.dataframe.operator.realtime.TaskReactorOperator;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;
import com.pl.gdl.runtime.variable.VariableManager;
import groovy.lang.Closure;

import java.util.*;

public class GdlExecutionContext {
    private static final ThreadLocal<GdlExecutionContext> CURRENT = ThreadLocal.withInitial(GdlExecutionContext::new);

    private ExecutionEngine executionEngine;
    private final DagGraph dagGraph = new DagGraph();
    private final Map<String, Object> scriptParameters = new LinkedHashMap<>();
    private final Set<String> registeredTempTables = new LinkedHashSet<>();
    private final List<String> comments = new ArrayList<>();
    private CmdDataframe returnDf;
    private int nodeSequence = 1;

    public GdlExecutionContext() {
        this.executionEngine = new InMemoryEngine();
    }

    public static GdlExecutionContext get() {
        return CURRENT.get();
    }

    public static void set(GdlExecutionContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public ExecutionEngine getExecutionEngine() {
        return executionEngine;
    }

    public void setExecutionEngine(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
    }

    public DagGraph getDagGraph() {
        return dagGraph;
    }

    public Map<String, Object> getScriptParameters() {
        return scriptParameters;
    }

    public void setScriptParameters(Map<String, Object> params) {
        if (params != null) {
            this.scriptParameters.putAll(params);
        }
    }

    public Set<String> getRegisteredTempTables() {
        return registeredTempTables;
    }

    public void registerTempTable(String tableName) {
        if (tableName != null) {
            registeredTempTables.add(tableName);
        }
    }

    public List<String> getComments() {
        return comments;
    }

    public void addComment(String comment) {
        if (comment != null) {
            this.comments.add(comment);
        }
    }

    public CmdDataframe getReturnDf() {
        return returnDf;
    }

    public void setReturnDf(CmdDataframe returnDf) {
        this.returnDf = returnDf;
    }

    public CmdDataframe createFrom(CmdDatasource ds, String table) {
        FromOperator op = new FromOperator(ds, table);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);
        registerTempTable(op.getTempTableName());

        dagGraph.addNode(new DagNode(nodeId, "from " + table, "FromOperator", "table"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public CmdDataframe createQuery(CmdDatasource ds, String sql) {
        QueryOperator op = new QueryOperator(ds, sql);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);
        registerTempTable(op.getTempTableName());

        dagGraph.addNode(new DagNode(nodeId, "query", "QueryOperator", "query"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public CmdDataframe createInsert(CmdDatasource ds, String targetTable, String sql) {
        InsertOperator op = new InsertOperator(ds, targetTable, sql);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);

        dagGraph.addNode(new DagNode(nodeId, "insert " + targetTable, "InsertOperator", "insert"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public CmdDataframe createPeriodReactor(String cronExpr) {
        PeriodReactorOperator op = new PeriodReactorOperator(cronExpr);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);

        dagGraph.addNode(new DagNode(nodeId, "periodReactor", "PeriodReactorOperator", "signal"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public CmdDataframe createTaskReactor(List<String> taskIds, int successRate, int delaySec) {
        TaskReactorOperator op = new TaskReactorOperator(taskIds, successRate, delaySec);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);

        dagGraph.addNode(new DagNode(nodeId, "taskReactor", "TaskReactorOperator", "signal"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public HttpOperator createHttp(String method, String url) {
        HttpOperator op = new HttpOperator(method, url);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);

        dagGraph.addNode(new DagNode(nodeId, "http " + method, "HttpOperator", "http"));
        return op;
    }

    public CmdDataframe createGroovy(Closure<RowDataFrame> closure) {
        GroovyCustomOperator op = new GroovyCustomOperator(closure);
        String nodeId = "node_" + (nodeSequence++);
        op.setNodeId(nodeId);

        dagGraph.addNode(new DagNode(nodeId, "groovy", "GroovyCustomOperator", "script"));
        return new CmdDataframeImpl(op, executionEngine);
    }

    public Object resolveVariable(String generatorName, Map<String, Object> params) {
        return VariableManager.getInstance().resolve(this, generatorName, params);
    }
}
