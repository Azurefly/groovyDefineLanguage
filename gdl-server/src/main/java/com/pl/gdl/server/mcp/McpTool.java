package com.pl.gdl.server.mcp;

import java.util.Map;

public interface McpTool {
    String getName();
    String getDescription();
    Map<String, Object> getInputSchema();
    Object execute(Map<String, Object> arguments) throws Exception;
}
