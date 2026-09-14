package com.pl.gdl.server.mcp;

import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.mcp.tool.GetTaskResultTool;
import com.pl.gdl.server.mcp.tool.GetTsmlToDagTool;
import com.pl.gdl.server.mcp.tool.StartTaskTool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class McpToolRegistry {
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    public McpToolRegistry(TreClient treClient) {
        register(new StartTaskTool(treClient));
        register(new GetTaskResultTool(treClient));
        register(new GetTsmlToDagTool(treClient));
    }

    public void register(McpTool tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public McpTool getTool(String name) {
        return tools.get(name);
    }

    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public Object executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
        }
        return tool.execute(arguments != null ? arguments : Map.of());
    }
}
