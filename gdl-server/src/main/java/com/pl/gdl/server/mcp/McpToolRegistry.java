package com.pl.gdl.server.mcp;

import com.pl.gdl.server.client.GdlEngineClient;
import com.pl.gdl.server.mcp.tool.GetTaskResultTool;
import com.pl.gdl.server.mcp.tool.GetGmlToDagTool;
import com.pl.gdl.server.mcp.tool.StartTaskTool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具注册表：集中管理对外暴露的 MCP 工具。
 *
 * <p>构造时自动注册默认工具集：{@code start_task}（提交 GDL 任务）、
 * {@code get_task_result}（查询任务结果）、{@code get_tsml_to_dag}
 * （GML 转 DAG）。同时支持运行时通过 {@link #register(McpTool)} 注册自定义工具。</p>
 */
public class McpToolRegistry {
    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();

    /**
     * 构造注册表并注册默认工具集。
     *
     * @param treClient 工具底层调用的任务客户端
     */
    public McpToolRegistry(GdlEngineClient treClient) {
        register(new StartTaskTool(treClient));
        register(new GetTaskResultTool(treClient));
        register(new GetGmlToDagTool(treClient));
    }

    /**
     * 注册一个 MCP 工具，同名工具会被覆盖。
     *
     * @param tool 待注册的工具；{@code null} 或名称为 {@code null} 时忽略
     */
    public void register(McpTool tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
        }
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名
     * @return 工具实例；不存在时返回 {@code null}
     */
    public McpTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * 返回全部已注册工具（不可修改视图）。
     *
     * @return 工具集合
     */
    public Collection<McpTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 执行指定名称的工具。
     *
     * @param toolName  工具名
     * @param arguments 工具入参，可为 {@code null}（此时视为空参数）
     * @return 工具执行结果
     * @throws Exception 工具不存在或执行失败时抛出
     */
    public Object executeTool(String toolName, Map<String, Object> arguments) throws Exception {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
        }
        return tool.execute(arguments != null ? arguments : Map.of());
    }
}
