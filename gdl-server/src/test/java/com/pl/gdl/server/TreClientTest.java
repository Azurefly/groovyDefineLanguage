package com.pl.gdl.server;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.client.TreClientImpl;
import com.pl.gdl.server.mcp.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TreClientTest {

    @Test
    public void testTreClientOntologyAndTaskExecution() {
        TreClient client = new TreClientImpl();

        // 1. Register Ontology
        String ontoCode = """
            package v1
            import com.pl.gdl.ontology.model.Ontology
            import com.pl.gdl.ontology.annotation.Table
            import com.pl.gdl.ontology.annotation.Column

            @Table(type="ORC", remarks="微信群聊")
            class ChatServerTest extends Ontology {
                @Column(remarks="群标识")
                String groupId = "group_id"

                ChatServerTest() {
                    this.oId = "TS_SERVER_001"
                    this.oName = "群聊测试"
                    this.oDesc = "测试本体"
                    this.oAuthor = "ServerAdmin"
                    this.oTable = "t_chat"
                }
            }
        """;
        RegisterRsp regRsp = client.registerOntology(ontoCode);
        assertThat(regRsp.getStatus()).isEqualTo(RegisterRsp.STATUS_SUCCESS);

        List<OntoInfoRsp> ontos = client.getOntologies("v1.ChatServerTest");
        assertThat(ontos).hasSize(1);
        assertThat(ontos.get(0).getOAuthor()).isEqualTo("ServerAdmin");

        // 2. Start Task
        String script = """
            def hiveDs = hive()
            def df = from(hiveDs, "dw.t_order").where("amount > 100").select("order_id, amount")
            returnDf(df)
        """;
        String taskId = client.startTask(script, Map.of("areaCode", "320100"));
        assertThat(taskId).isNotNull();

        TaskResult result = client.getTaskResult(taskId);
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("FINISHED");

        // 3. Get DAG
        String dagJson = client.getTsmlToDag(script);
        assertThat(dagJson).contains("\"canvas\"");
        assertThat(dagJson).contains("FromOperator");
    }

    @Test
    public void testMcpToolsExecution() throws Exception {
        TreClient client = new TreClientImpl();
        McpToolRegistry mcpRegistry = new McpToolRegistry(client);

        assertThat(mcpRegistry.getAllTools()).hasSize(3);

        // Test start_task tool
        String script = """
            def hiveDs = hive()
            def df = from(hiveDs, "t_user").select("id, name")
            returnDf(df)
        """;
        Object startRes = mcpRegistry.executeTool("start_task", Map.of("code", script, "params", "city=NJ;region=East"));
        assertThat(startRes).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> startMap = (Map<String, Object>) startRes;
        String taskId = (String) startMap.get("taskId");
        assertThat(taskId).isNotNull();

        // Test get_task_result tool
        Object resultRes = mcpRegistry.executeTool("get_task_result", Map.of("taskId", taskId));
        assertThat(resultRes).isInstanceOf(TaskResult.class);

        // Test get_tsml_to_dag tool
        Object dagRes = mcpRegistry.executeTool("get_tsml_to_dag", Map.of("code", script));
        assertThat(dagRes.toString()).contains("\"canvas\"");
    }
}
