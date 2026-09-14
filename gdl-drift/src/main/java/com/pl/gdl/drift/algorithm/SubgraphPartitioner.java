package com.pl.gdl.drift.algorithm;

import com.pl.gdl.drift.model.CutEdge;
import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.model.ExecutionSubgraph;
import com.pl.gdl.drift.transport.IntermediateTableManager;
import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;

import java.util.*;

public class SubgraphPartitioner {

    public DriftPlan partition(String originalScript, DagGraph originalGraph, String localAreaCode) {
        String effectiveLocalArea = localAreaCode != null ? localAreaCode : "local";
        DriftPlan plan = new DriftPlan(originalScript, effectiveLocalArea);

        Map<String, ExecutionSubgraph> subgraphs = new LinkedHashMap<>();

        // Group nodes by areaCode
        for (DagNode node : originalGraph.getNodes()) {
            String nodeArea = node.getAreaCode() != null ? node.getAreaCode() : effectiveLocalArea;
            ExecutionSubgraph sg = subgraphs.computeIfAbsent(nodeArea, a ->
                    new ExecutionSubgraph(a, a.equalsIgnoreCase(effectiveLocalArea) || a.equalsIgnoreCase("local")));
            sg.addNode(node);
        }

        // Find cut edges crossing different areaCodes
        for (DagEdge edge : originalGraph.getEdges()) {
            DagNode src = originalGraph.getNode(edge.getSourceNode());
            DagNode tgt = originalGraph.getNode(edge.getTargetNode());
            if (src == null || tgt == null) continue;

            String srcArea = src.getAreaCode() != null ? src.getAreaCode() : effectiveLocalArea;
            String tgtArea = tgt.getAreaCode() != null ? tgt.getAreaCode() : effectiveLocalArea;

            if (srcArea.equalsIgnoreCase(tgtArea)) {
                ExecutionSubgraph sg = subgraphs.get(srcArea);
                if (sg != null) sg.addEdge(edge);
            } else {
                // Cross-boundary edge
                String tempTbl = IntermediateTableManager.getInstance().generateTempTableName();
                CutEdge cut = new CutEdge(edge, src, tgt, srcArea, tgtArea, tempTbl, "frc");
                plan.addCut(cut);

                ExecutionSubgraph srcSg = subgraphs.get(srcArea);
                if (srcSg != null) srcSg.addOutboundCut(cut);

                ExecutionSubgraph tgtSg = subgraphs.get(tgtArea);
                if (tgtSg != null) tgtSg.addInboundCut(cut);
            }
        }

        for (Map.Entry<String, ExecutionSubgraph> entry : subgraphs.entrySet()) {
            if (entry.getValue().isLocal()) {
                plan.setLocalSubgraph(entry.getValue());
            } else {
                plan.addRemoteSubgraph(entry.getKey(), entry.getValue());
            }
        }

        if (plan.getLocalSubgraph() == null) {
            plan.setLocalSubgraph(new ExecutionSubgraph(effectiveLocalArea, true));
        }

        return plan;
    }
}
