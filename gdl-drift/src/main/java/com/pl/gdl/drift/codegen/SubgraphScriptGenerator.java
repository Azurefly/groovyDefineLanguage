package com.pl.gdl.drift.codegen;

import com.pl.gdl.drift.model.CutEdge;
import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.model.ExecutionSubgraph;

import java.util.Map;

public class SubgraphScriptGenerator {

    public void generateScripts(DriftPlan plan) {
        // Generate remote subgraphs
        for (Map.Entry<String, ExecutionSubgraph> entry : plan.getRemoteSubgraphs().entrySet()) {
            ExecutionSubgraph remoteSg = entry.getValue();
            StringBuilder sb = new StringBuilder();
            sb.append("// Auto-generated Remote Subgraph for AreaCode: ").append(remoteSg.getAreaCode()).append("\n");
            sb.append("def hiveDs = hive().areaCode(\"").append(remoteSg.getAreaCode()).append("\")\n");

            for (CutEdge cut : remoteSg.getOutboundCuts()) {
                sb.append("// Remote processing and driftTo shipping\n");
                sb.append("def df_").append(cut.getSourceNode().getId()).append(" = from(hiveDs, \"source_table\")\n");
                sb.append("driftTo(\"").append(cut.getTargetAreaCode()).append("\").attach(df_")
                  .append(cut.getSourceNode().getId()).append(", \"\", \"")
                  .append(cut.getIntermediateTableName()).append("\", \"")
                  .append(cut.getExchangeType()).append("\")\n");
            }
            remoteSg.setGeneratedScript(sb.toString());
        }

        // Generate local subgraph
        ExecutionSubgraph localSg = plan.getLocalSubgraph();
        if (localSg != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("// Auto-generated Local Subgraph for AreaCode: ").append(localSg.getAreaCode()).append("\n");
            sb.append("def hiveLocal = hive()\n");

            for (CutEdge cut : localSg.getInboundCuts()) {
                sb.append("// Receive drifted dataset\n");
                sb.append("def df_").append(cut.getTargetNode().getId()).append(" = driftFrom(hiveLocal, \"")
                  .append(cut.getIntermediateTableName()).append("\", \"")
                  .append(cut.getExchangeType()).append("\")\n");
            }
            localSg.setGeneratedScript(sb.toString());
        }
    }
}
