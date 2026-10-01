package com.pl.gdl.drift.codegen;

import com.pl.gdl.drift.model.CutEdge;
import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.model.ExecutionSubgraph;

import java.util.Map;

/**
 * 子图脚本生成器：为漂移计划中的远端子图 / 本地子图生成可执行的 GDL 脚本。
 */
public class SubgraphScriptGenerator {

    public void generateScripts(DriftPlan plan) {
        // Generate remote subgraphs
        for (Map.Entry<String, ExecutionSubgraph> entry : plan.getRemoteSubgraphs().entrySet()) {
            ExecutionSubgraph remoteSg = entry.getValue();
            StringBuilder sb = new StringBuilder();
            sb.append("// Auto-generated Remote Subgraph for AreaCode: ").append(escapeGroovyString(remoteSg.getAreaCode())).append("\n");
            sb.append("def hiveDs = hive().areaCode(\"").append(escapeGroovyString(remoteSg.getAreaCode())).append("\")\n");

            for (CutEdge cut : remoteSg.getOutboundCuts()) {
                sb.append("// Remote processing and driftTo shipping\n");
                // 原型桩实现：远端子图的源表尚未从真实元数据/血缘生成，
                // 当前使用占位表名 "source_table"，生产使用前需替换为真实源表推导逻辑
                sb.append("def df_").append(sanitizeVarName(cut.getSourceNode().getId()))
                  .append(" = from(hiveDs, \"source_table\")\n");
                sb.append("driftTo(\"").append(escapeGroovyString(cut.getTargetAreaCode())).append("\").attach(df_")
                  .append(sanitizeVarName(cut.getSourceNode().getId())).append(", \"\", \"")
                  .append(escapeGroovyString(cut.getIntermediateTableName())).append("\", \"")
                  .append(escapeGroovyString(cut.getExchangeType())).append("\")\n");
            }
            remoteSg.setGeneratedScript(sb.toString());
        }

        // Generate local subgraph
        ExecutionSubgraph localSg = plan.getLocalSubgraph();
        if (localSg != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("// Auto-generated Local Subgraph for AreaCode: ").append(escapeGroovyString(localSg.getAreaCode())).append("\n");
            sb.append("def hiveLocal = hive()\n");

            for (CutEdge cut : localSg.getInboundCuts()) {
                sb.append("// Receive drifted dataset\n");
                sb.append("def df_").append(sanitizeVarName(cut.getTargetNode().getId()))
                  .append(" = driftFrom(hiveLocal, \"")
                  .append(escapeGroovyString(cut.getIntermediateTableName())).append("\", \"")
                  .append(escapeGroovyString(cut.getExchangeType())).append("\")\n");
            }
            localSg.setGeneratedScript(sb.toString());
        }
    }

    /**
     * 对拼入 Groovy 双引号字符串字面量的值做转义，防止 {@code $} 触发 GString 插值、
     * 引号 / 反斜杠破坏脚本结构。
     */
    public static String escapeGroovyString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '$': sb.append("\\$"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 把外部传入的标识净化为合法的 Groovy 变量名：非法字符转下划线，
     * 数字开头补下划线前缀，空值回退为 {@code "v"}。
     */
    public static String sanitizeVarName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "v";
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty()) {
            return "v";
        }
        if (Character.isDigit(cleaned.charAt(0))) {
            cleaned = "_" + cleaned;
        }
        return cleaned;
    }
}
