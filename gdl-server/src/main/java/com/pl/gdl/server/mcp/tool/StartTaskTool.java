package com.pl.gdl.server.mcp.tool;

import com.pl.gdl.server.client.GdlEngineClient;
import com.pl.gdl.server.mcp.McpTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具 {@code start_task}：把一段 GML 脚本提交到 GDL 引擎执行。
 *
 * <p>任务提交为异步：调用后立即返回 taskId，可随后用 {@code get_task_result}
 * 工具轮询执行结果。</p>
 */
public class StartTaskTool implements McpTool {
    private final GdlEngineClient treClient;

    public StartTaskTool(GdlEngineClient treClient) {
        this.treClient = treClient;
    }

    @Override
    public String getName() {
        return "start_task";
    }

    @Override
    public String getDescription() {
        return "Dispatches a GML script for execution on the GDL engine";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        // 注意：schema 中刻意不包含历史拼写错误的 "bussinessId" 字段——
        // GdlEngineClient#startTask(String, Map) 只接收脚本与 params，下游没有任何逻辑
        // 使用该字段，保留它只会误导调用方传入无用参数。
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "GDL script code"),
                        "params", Map.of("type", "string", "description", "Script parameters e.g. key1=val1;key2=val2")
                ),
                "required", java.util.List.of("code")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String code = (String) arguments.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Parameter 'code' is mandatory");
        }

        Map<String, Object> paramMap = new LinkedHashMap<>();
        Object rawParams = arguments.get("params");
        if (rawParams instanceof Map<?, ?> m) {
            m.forEach((k, v) -> paramMap.put(String.valueOf(k), v));
        } else if (rawParams instanceof String str && !str.isBlank()) {
            for (String pair : str.split(";")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    paramMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        String taskId = treClient.startTask(code, paramMap);
        return Map.of("taskId", taskId, "status", "SUBMITTED");
    }
}
