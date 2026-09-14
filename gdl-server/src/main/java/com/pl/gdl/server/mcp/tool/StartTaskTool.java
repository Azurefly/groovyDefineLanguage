package com.pl.gdl.server.mcp.tool;

import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.mcp.McpTool;

import java.util.LinkedHashMap;
import java.util.Map;

public class StartTaskTool implements McpTool {
    private final TreClient treClient;

    public StartTaskTool(TreClient treClient) {
        this.treClient = treClient;
    }

    @Override
    public String getName() {
        return "start_task";
    }

    @Override
    public String getDescription() {
        return "Dispatches a GDL / TSML script for execution on the TRE engine";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "GDL script code"),
                        "bussinessId", Map.of("type", "string", "description", "Business domain ID"),
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
