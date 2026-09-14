package com.pl.gdl.server.mcp.tool;

import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.mcp.McpTool;

import java.util.Map;

public class GetTsmlToDagTool implements McpTool {
    private final TreClient treClient;

    public GetTsmlToDagTool(TreClient treClient) {
        this.treClient = treClient;
    }

    @Override
    public String getName() {
        return "get_tsml_to_dag";
    }

    @Override
    public String getDescription() {
        return "Converts a GDL / TSML script into a visual DAG canvas JSON structure";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "GDL script code")
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
        return treClient.getTsmlToDag(code);
    }
}
