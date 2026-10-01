package com.pl.gdl.drift.algorithm;

import com.pl.gdl.common.constant.GdlConstants;
import com.pl.gdl.drift.model.CutEdge;
import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.model.ExecutionSubgraph;
import com.pl.gdl.drift.transport.IntermediateTableManager;
import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;

import java.util.*;

/**
 * 子图切分器：按地域编码把完整 DAG 切分为本地子图与远端子图，
 * 跨地域的边记录为 {@link CutEdge}（通过中间表做数据漂移）。
 *
 * <p>地域编码统一做规范化（见 {@link #normalizeAreaCode(String)}），
 * 未指定时默认使用 {@link GdlConstants#DEFAULT_AREA_CODE}。</p>
 */
public class SubgraphPartitioner {

    /**
     * 规范化地域编码：trim + 转小写（{@link Locale#ROOT}）；
     * 为空时回退为 {@link GdlConstants#DEFAULT_AREA_CODE}。
     */
    public static String normalizeAreaCode(String areaCode) {
        if (areaCode == null || areaCode.isBlank()) {
            return GdlConstants.DEFAULT_AREA_CODE;
        }
        return areaCode.trim().toLowerCase(Locale.ROOT);
    }

    public DriftPlan partition(String originalScript, DagGraph originalGraph, String localAreaCode) {
        String effectiveLocalArea = normalizeAreaCode(localAreaCode);
        DriftPlan plan = new DriftPlan(originalScript, effectiveLocalArea);

        Map<String, ExecutionSubgraph> subgraphs = new LinkedHashMap<>();

        // Group nodes by areaCode
        for (DagNode node : originalGraph.getNodes()) {
            String nodeArea = node.getAreaCode() != null ? normalizeAreaCode(node.getAreaCode()) : effectiveLocalArea;
            ExecutionSubgraph sg = subgraphs.computeIfAbsent(nodeArea, a ->
                    new ExecutionSubgraph(a, a.equalsIgnoreCase(effectiveLocalArea)
                            || GdlConstants.DEFAULT_AREA_CODE.equalsIgnoreCase(a)));
            sg.addNode(node);
        }

        // Find cut edges crossing different areaCodes
        for (DagEdge edge : originalGraph.getEdges()) {
            DagNode src = originalGraph.getNode(edge.getSourceNode());
            DagNode tgt = originalGraph.getNode(edge.getTargetNode());
            if (src == null || tgt == null) continue;

            String srcArea = src.getAreaCode() != null ? normalizeAreaCode(src.getAreaCode()) : effectiveLocalArea;
            String tgtArea = tgt.getAreaCode() != null ? normalizeAreaCode(tgt.getAreaCode()) : effectiveLocalArea;

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
