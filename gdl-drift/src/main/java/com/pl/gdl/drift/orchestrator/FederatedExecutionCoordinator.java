package com.pl.gdl.drift.orchestrator;

import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.drift.algorithm.SubgraphPartitioner;
import com.pl.gdl.drift.codegen.SubgraphScriptGenerator;
import com.pl.gdl.drift.model.CutEdge;
import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.transport.IntermediateTableManager;
import com.pl.gdl.runtime.compiler.GdlCompiler;
import com.pl.gdl.runtime.dag.DagGraph;

import java.util.Map;

public class FederatedExecutionCoordinator {
    private final SubgraphPartitioner partitioner = new SubgraphPartitioner();
    private final SubgraphScriptGenerator scriptGenerator = new SubgraphScriptGenerator();
    private final GdlCompiler compiler = new GdlCompiler();

    public TaskResult executeFederated(String gdlScript, String localAreaCode, Map<String, Object> params) {
        String effectiveArea = localAreaCode != null ? localAreaCode : "320000";
        DagGraph fullDag = compiler.parseToDag(gdlScript, params);

        DriftPlan plan = partitioner.partition(gdlScript, fullDag, effectiveArea);
        scriptGenerator.generateScripts(plan);

        TaskResult taskResult = new TaskResult("TASK_" + System.currentTimeMillis(), "FINISHED");

        try {
            if (!plan.hasDrift()) {
                // Direct local execution
                compiler.execute(gdlScript, params);
                taskResult.addAreaResult(new TaskResult.AreaResult(effectiveArea, java.util.List.of(), java.util.List.of()));
            } else {
                // 1. Simulate dispatch to remote subgraphs
                for (String remoteArea : plan.getRemoteSubgraphs().keySet()) {
                    taskResult.addAreaResult(new TaskResult.AreaResult(remoteArea, java.util.List.of(), java.util.List.of()));
                }

                // 2. Execute local subgraph
                if (plan.getLocalSubgraph() != null && plan.getLocalSubgraph().getGeneratedScript() != null) {
                    compiler.execute(plan.getLocalSubgraph().getGeneratedScript(), params);
                }
                taskResult.addAreaResult(new TaskResult.AreaResult(effectiveArea, java.util.List.of(), java.util.List.of()));
            }
        } finally {
            // Guarantee cleanup of intermediate tables
            for (CutEdge cut : plan.getAllCuts()) {
                IntermediateTableManager.getInstance().release(cut.getIntermediateTableName());
            }
        }

        return taskResult;
    }

    public DriftPlan planDrift(String gdlScript, String localAreaCode, Map<String, Object> params) {
        DagGraph fullDag = compiler.parseToDag(gdlScript, params);
        DriftPlan plan = partitioner.partition(gdlScript, fullDag, localAreaCode);
        scriptGenerator.generateScripts(plan);
        return plan;
    }
}
