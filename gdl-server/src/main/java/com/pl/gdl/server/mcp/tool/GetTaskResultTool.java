package com.pl.gdl.server.mcp.tool;

import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.mcp.McpTool;

import java.util.Map;

public class GetTaskResultTool implements McpTool {
    private final TreClient treClient;

    public GetTaskResultTool(TreClient treClient) {
        this.treClient = treClient;
    }

    @Override
    public String getName() {
        return "get_task_result";
    }

    @Override
    public String getDescription() {
        return "Retrieves the execution result of a task by its taskId";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "taskId", Map.of("type", "string", "description", "Task execution ID")
                ),
                "required", java.util.List.of("taskId")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String taskId = (String) arguments.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Parameter 'taskId' is mandatory");
        }
        TaskResult result = treClient.getTaskResult(taskId);
        if (result == null) {
            return Map.of("status", "NOT_FOUND", "message", "Task " + taskId + " not found");
        }
        return result;
    }
}
