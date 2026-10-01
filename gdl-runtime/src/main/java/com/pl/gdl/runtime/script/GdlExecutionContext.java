package com.pl.gdl.runtime.script;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.advanced.GroovyCustomOperator;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.dataframe.operator.base.InsertOperator;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.dataframe.operator.output.ToOperator;
import com.pl.gdl.dataframe.operator.realtime.PeriodReactorOperator;
import com.pl.gdl.dataframe.operator.realtime.TaskReactorOperator;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;
import com.pl.gdl.runtime.federation.FederatedJoinExecutor;
import com.pl.gdl.runtime.federation.FederatedJoinRequest;
import com.pl.gdl.runtime.federation.FederatedJoinResult;
import com.pl.gdl.runtime.plan.DatasourceExecutionPlanner;
import com.pl.gdl.runtime.plan.ExecutionIntent;
import com.pl.gdl.runtime.plan.ExecutionPlan;
import com.pl.gdl.runtime.variable.VariableManager;
import groovy.lang.Closure;

import java.util.*;

public class GdlExecutionContext {
    private static final ThreadLocal<GdlExecutionContext> CURRENT = ThreadLocal.withInitial(GdlExecutionContext::new);

    private ExecutionEngine executionEngine;
    private DatasourceRegistry datasourceRegistry;
    private DatasourceExecutionPlanner executionPlanner;
    private final List<ExecutionPlan> executionPlans = new ArrayList<>();
    private final List<FederatedJoinResult> federatedJoinResults = new ArrayList<>();
    private final DagGraph dagGraph = new DagGraph();
    private final Map<String, Object> scriptParameters = new LinkedHashMap<>();
    private final Set<String> registeredTempTables = new LinkedHashSet<>();
    private final List<String> comments = new ArrayList<>();
    private CmdDataframe returnDf;
    private int nodeSequence = 1;

    public GdlExecutionContext() {
        this.executionEngine = new InMemoryEngine();
        this.datasourceRegistry = DatasourceRegistry.getDefault();
        this.executionPlanner = new DatasourceExecutionPlanner();
        LogicalOperator.setGlobalListener(this::recordOperator);
    }

    public static GdlExecutionContext get() {
        GdlExecutionContext ctx = CURRENT.get();
        LogicalOperator.setGlobalListener(ctx::recordOperator);
        return ctx;
    }

    public static void set(GdlExecutionContext context) {
        CURRENT.set(context);
        if (context != null) {
            LogicalOperator.setGlobalListener(context::recordOperator);
        } else {
            LogicalOperator.setGlobalListener(null);
        }
    }

    public static void clear() {
        LogicalOperator.setGlobalListener(null);
        CURRENT.remove();
    }

    public ExecutionEngine getExecutionEngine() { return executionEngine; }
    public void setExecutionEngine(ExecutionEngine executionEngine) { this.executionEngine = executionEngine; }
    public DatasourceRegistry getDatasourceRegistry() { return datasourceRegistry; }
    public void setDatasourceRegistry(DatasourceRegistry datasourceRegistry) {
        this.datasourceRegistry = datasourceRegistry != null ? datasourceRegistry : DatasourceRegistry.getDefault();
    }
    public DatasourceExecutionPlanner getExecutionPlanner() { return executionPlanner; }
    public void setExecutionPlanner(DatasourceExecutionPlanner executionPlanner) {
        this.executionPlanner = executionPlanner != null ? executionPlanner : new DatasourceExecutionPlanner();
    }

    public List<ExecutionPlan> getExecutionPlans() { return Collections.unmodifiableList(executionPlans); }
    public List<FederatedJoinResult> getFederatedJoinResults() { return Collections.unmodifiableList(federatedJoinResults); }
    public DagGraph getDagGraph() { return dagGraph; }
    public Map<String, Object> getScriptParameters() { return scriptParameters; }

    public void setScriptParameters(Map<String, Object> params) {
        if (params != null) this.scriptParameters.putAll(params);
    }

    public Set<String> getRegisteredTempTables() { return registeredTempTables; }
    public void registerTempTable(String tableName) { if (tableName != null) registeredTempTables.add(tableName); }
    public List<String> getComments() { return comments; }
    public void addComment(String comment) { if (comment != null) this.comments.add(comment); }
    public CmdDataframe getReturnDf() { return returnDf; }
    public void setReturnDf(CmdDataframe returnDf) { this.returnDf = returnDf; }

    public void recordOperator(LogicalOperator op) {
        if (op == null || op.getNodeId() == null) return;
        String id = op.getNodeId();
        String label = op.getOperatorName();
        String type = "operator";

        if (op instanceof FromOperator fromOp) {
            label = "from " + fromOp.getTableName();
            type = "table";
        } else if (op instanceof ToOperator toOp) {
            label = "to " + toOp.getTargetTableName();
            type = "table";
        } else if (op instanceof QueryOperator) {
            type = "query";
        } else if (op instanceof InsertOperator) {
            type = "insert";
        }

        DagNode node = new DagNode(id, label, op.getClass().getSimpleName(), type);
        node.setAreaCode(op.getAreaCode());
        dagGraph.addNode(node);

        for (LogicalOperator up : op.getUpstream()) {
            if (up.getNodeId() != null && !up.getNodeId().equals(id)) {
                dagGraph.addEdge(up.getNodeId(), id);
            }
        }
        for (LogicalOperator dep : op.getDependencies()) {
            if (dep.getNodeId() != null && !dep.getNodeId().equals(id)) {
                dagGraph.addEdge(dep.getNodeId(), id);
            }
        }
    }

    public CmdDataframe createFrom(CmdDatasource ds, String table) {
        FromOperator op = new FromOperator(ds, table);
        registerTempTable(op.getTempTableName());
        ExecutionPlan plan = plan(ds, ExecutionIntent.READ);
        if (op.getNodeId() != null) {
            dagGraph.addNode(datasourceNode(op.getNodeId(), "from " + table, "FromOperator", "table", ds, plan));
        }
        return new CmdDataframeImpl(op, plan.engine());
    }

    public CmdDataframe createQuery(CmdDatasource ds, String sql) {
        QueryOperator op = new QueryOperator(ds, sql);
        registerTempTable(op.getTempTableName());
        ExecutionPlan plan = plan(ds, ExecutionIntent.SQL_READ);
        if (op.getNodeId() != null) {
            dagGraph.addNode(datasourceNode(op.getNodeId(), "query", "QueryOperator", "query", ds, plan));
        }
        return new CmdDataframeImpl(op, plan.engine());
    }

    public CmdDataframe createInsert(CmdDatasource ds, String targetTable, String sql) {
        InsertOperator op = new InsertOperator(ds, targetTable, sql);
        ExecutionPlan plan = plan(ds, ExecutionIntent.SQL_WRITE);
        if (op.getNodeId() != null) {
            dagGraph.addNode(datasourceNode(op.getNodeId(), "insert " + targetTable, "InsertOperator", "insert", ds, plan));
        }
        return new CmdDataframeImpl(op, plan.engine());
    }

    public FederatedJoinResult executeFederatedJoin(FederatedJoinRequest request) {
        FederatedJoinExecutor executor = new FederatedJoinExecutor(datasourceRegistry, executionPlanner, executionEngine);
        FederatedJoinResult result = executor.execute(request);
        executionPlans.addAll(result.sourcePlans());
        federatedJoinResults.add(result);
        return result;
    }

    private ExecutionPlan plan(CmdDatasource datasource, ExecutionIntent intent) {
        if (datasourceRegistry == null || executionPlanner == null) {
            String type = datasource == null ? "LOCAL_ENGINE" : datasource.getDatasourceType();
            return new ExecutionPlan(ExecutionPlan.Mode.FALLBACK, intent, type, executionEngine,
                    "planner unavailable; using context execution engine");
        }
        ExecutionPlan plan = executionPlanner.plan(datasource, intent, datasourceRegistry, executionEngine);
        executionPlans.add(plan);
        return plan;
    }

    private DagNode datasourceNode(String id, String label, String operator, String type,
                                   CmdDatasource datasource, ExecutionPlan plan) {
        DagNode node = new DagNode(id, label, operator, type);
        if (datasource != null) {
            node.setAreaCode(datasource.getAreaCode());
            node.setProperty("datasourceType", datasource.getDatasourceType());
            node.setProperty("datasourceName", datasource.getDsConfName());
        }
        if (plan != null) {
            node.setProperty("executionMode", plan.mode().name());
            node.setProperty("executionIntent", plan.intent().name());
            node.setProperty("executionReason", plan.reason());
        }
        return node;
    }

    public CmdDataframe createPeriodReactor(String cronExpr) {
        PeriodReactorOperator op = new PeriodReactorOperator(cronExpr);
        return new CmdDataframeImpl(op, executionEngine);
    }

    public CmdDataframe createTaskReactor(List<String> taskIds, int successRate, int delaySec) {
        TaskReactorOperator op = new TaskReactorOperator(taskIds, successRate, delaySec);
        return new CmdDataframeImpl(op, executionEngine);
    }

    public HttpOperator createHttp(String method, String url) {
        return new HttpOperator(method, url);
    }

    public CmdDataframe createGroovy(Closure<RowDataFrame> closure) {
        GroovyCustomOperator op = new GroovyCustomOperator(closure);
        return new CmdDataframeImpl(op, executionEngine);
    }

    public Object resolveVariable(String generatorName, Map<String, Object> params) {
        return VariableManager.getInstance().resolve(this, generatorName, params);
    }
}
