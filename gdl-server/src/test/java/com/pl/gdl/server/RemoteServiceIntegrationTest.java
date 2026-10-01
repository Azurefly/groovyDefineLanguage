package com.pl.gdl.server;

import com.pl.gdl.common.enums.TaskStatus;
import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.client.TreRemoteHttpClient;
import com.pl.gdl.server.config.ServerConfig;
import com.pl.gdl.server.http.GdlHttpServer;
import com.pl.gdl.server.http.HttpRequest;
import com.pl.gdl.server.http.HttpResponse;
import com.pl.gdl.server.http.InProcessHttpTransport;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RemoteServiceIntegrationTest {

    private static GdlHttpServer server;
    private static TreClient remoteClient;
    private static InProcessHttpTransport transport;
    private static final String TEST_TOKEN = "tre-secret-token-123";

    @BeforeAll
    public static void setUp() throws Exception {
        ServerConfig config = new ServerConfig(8080, TEST_TOKEN);
        server = new GdlHttpServer(config);
        server.start();

        transport = new InProcessHttpTransport(server);
        remoteClient = new TreRemoteHttpClient(transport, TEST_TOKEN);
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testHealthCheck() throws Exception {
        HttpRequest req = new HttpRequest("GET", "/tre/api/health", null);
        HttpResponse resp = transport.execute(req);

        assertThat(resp.getStatusCode()).isEqualTo(200);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
        assertThat(resp.getBody()).contains("1.0.0-GA");
    }

    @Test
    public void testRemoteOntologyLifecycle() {
        String ontoCode = """
            package v1
            import com.pl.gdl.ontology.model.Ontology
            import com.pl.gdl.ontology.annotation.Table
            import com.pl.gdl.ontology.annotation.Column

            @Table(type="ORC", remarks="远程第三方群聊测试")
            class RemoteChat extends Ontology {
                @Column(remarks="群标识")
                String groupId = "group_id"
                @Column(remarks="用户标识")
                String userId = "user_id"

                RemoteChat() {
                    this.oId = "TS_REMOTE_001"
                    this.oName = "远程群聊本体"
                    this.oDesc = "用于第三方远程调用测试"
                    this.oAuthor = "RemoteDeveloper"
                    this.oTable = "t_remote_chat"
                }
            }
        """;

        // 1. Register ontology remotely via HTTP
        RegisterRsp regRsp = remoteClient.registerOntology(ontoCode);
        assertThat(regRsp.getStatus()).isEqualTo(RegisterRsp.STATUS_SUCCESS);

        // 2. Query ontology metadata remotely via HTTP GET
        List<OntoInfoRsp> ontos = remoteClient.getOntologies("v1.RemoteChat", "local");
        assertThat(ontos).hasSize(1);
        OntoInfoRsp chat = ontos.get(0);
        assertThat(chat.getClassName()).isEqualTo("RemoteChat");
        assertThat(chat.getOAuthor()).isEqualTo("RemoteDeveloper");
        assertThat(chat.getFields()).hasSize(2);

        // 3. Unregister ontology remotely via HTTP POST
        RegisterRsp unregRsp = remoteClient.unregisterOntology("v1.RemoteChat");
        assertThat(unregRsp.getStatus()).isEqualTo(RegisterRsp.STATUS_SUCCESS);
    }

    @Test
    public void testRemoteTaskExecutionAndResultQuery() throws Exception {
        String gdlScript = """
            def hiveDs = hive()
            def df = from(hiveDs, "dw.t_person")
                .where("age > 18")
                .select("id, name, age")
            returnDf(df)
        """;

        // 1. Submit GDL task remotely via HTTP POST /tre/api/startTask
        String taskId = remoteClient.startTask(gdlScript, Map.of("areaCode", "320100"));
        assertThat(taskId).isNotNull().isNotBlank();

        // 2. Query task result remotely via HTTP GET /tre/api/getTaskResult?taskId=xxx
        //    任务为异步执行：轮询等待完成
        TaskResult result = null;
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            result = remoteClient.getTaskResult(taskId);
            if (result != null && TaskStatus.FINISHED.equals(result.getStatus())) {
                break;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(result).isNotNull();
        assertThat(result.getTaskId()).isEqualTo(taskId);
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FINISHED);
    }

    @Test
    public void testRemoteGetTsmlToDag() {
        String gdlScript = """
            def hiveDs = hive()
            def df1 = from(hiveDs, "dw.t_source").nodeId("node_1")
            def df2 = df1.where("status = 1").select("id, name").nodeId("node_2")
            df2.to(hiveDs, "dw.t_sink").nodeId("node_3")
        """;

        String dagJson = remoteClient.getTsmlToDag(gdlScript);
        assertThat(dagJson).contains("\"canvas\"");
        assertThat(dagJson).contains("node_1");
        assertThat(dagJson).contains("node_2");
        assertThat(dagJson).contains("node_3");
    }

    @Test
    public void testMcpStreamableHttpService() throws Exception {
        // Test MCP tools/list
        HttpRequest listReq = new HttpRequest("POST", "/tre/mcp/service", JsonOutput.toJson(Map.of("method", "tools/list")));
        listReq.addHeader("tre-token", TEST_TOKEN);
        HttpResponse listResp = transport.execute(listReq);

        assertThat(listResp.getStatusCode()).isEqualTo(200);
        assertThat(listResp.getBody()).contains("start_task");
        assertThat(listResp.getBody()).contains("get_task_result");
        assertThat(listResp.getBody()).contains("get_tsml_to_dag");

        // Test MCP tools/call for start_task
        Map<String, Object> callReq = Map.of(
                "method", "tools/call",
                "params", Map.of(
                        "name", "start_task",
                        "arguments", Map.of(
                                "code", "def hiveDs = hive()\ndef df = from(hiveDs, 't_orders').select('id')\nreturnDf(df)"
                        )
                )
        );

        HttpRequest execReq = new HttpRequest("POST", "/tre/mcp/service", JsonOutput.toJson(callReq));
        execReq.addHeader("tre-token", TEST_TOKEN);
        HttpResponse execResp = transport.execute(execReq);

        assertThat(execResp.getStatusCode()).isEqualTo(200);
        assertThat(execResp.getBody()).contains("SUBMITTED");
        assertThat(execResp.getBody()).contains("taskId");
    }

    @Test
    public void testTokenAuthenticationFailure() {
        // Client with incorrect token
        TreClient badClient = new TreRemoteHttpClient(transport, "wrong-token");

        assertThatThrownBy(() -> badClient.getOntologies(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("401");
    }

    @Test
    public void testMissingTokenReturns401() {
        // Client without any token (single-arg constructor) accessing a protected endpoint
        TreClient noAuthClient = new TreRemoteHttpClient(transport);

        assertThatThrownBy(() -> noAuthClient.getOntologies(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("401");
    }
}
