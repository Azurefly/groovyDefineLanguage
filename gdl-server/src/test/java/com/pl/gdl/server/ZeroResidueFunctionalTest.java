package com.pl.gdl.server;

import com.pl.gdl.common.constant.GdlConstants;
import com.pl.gdl.runtime.variable.VariableManager;
import com.pl.gdl.server.config.ServerConfig;
import com.pl.gdl.server.http.GdlHttpServer;
import com.pl.gdl.server.http.HttpRequest;
import com.pl.gdl.server.http.HttpResponse;
import com.pl.gdl.server.http.InProcessHttpTransport;
import groovy.json.JsonSlurper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 零残留清理后的功能测试：验证所有 tre/tsml 标识已替换为 gdl/gml，
 * 且系统功能正常。
 */
public class ZeroResidueFunctionalTest {

    private static GdlHttpServer server;
    private static InProcessHttpTransport transport;
    private static final String TEST_TOKEN = "zero-residue-test-token";

    @BeforeAll
    public static void setUp() throws Exception {
        ServerConfig config = new ServerConfig(8080, TEST_TOKEN);
        server = new GdlHttpServer(config);
        server.start();
        transport = new InProcessHttpTransport(server);
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHealthResponseUsesGdlEngineServiceName() throws Exception {
        HttpRequest req = new HttpRequest("GET", "/gdl/api/health", null);
        HttpResponse resp = transport.execute(req);
        assertThat(resp.getStatusCode()).isEqualTo(200);
        Map<String, Object> body = (Map<String, Object>) new JsonSlurper().parseText(resp.getBody());
        assertThat(body.get("service")).isEqualTo("GDL Engine");
        assertThat(body.get("status")).isEqualTo("UP");
    }

    @Test
    public void testOldTrePathsAreGone() throws Exception {
        // 旧路径已不存在：带正确 token 应返回 404（而非 200）
        HttpRequest req = new HttpRequest("GET", "/tre/api/health", null);
        req.addHeader("gdl-token", TEST_TOKEN);
        HttpResponse resp = transport.execute(req);
        assertThat(resp.getStatusCode()).isEqualTo(404);
    }

    @Test
    public void testGdlTokenHeaderIsAccepted() throws Exception {
        // gdl-token 请求头应被接受
        HttpRequest req = new HttpRequest("GET", "/gdl/api/getOntologies", null);
        req.addHeader("gdl-token", TEST_TOKEN);
        HttpResponse resp = transport.execute(req);
        assertThat(resp.getStatusCode()).isEqualTo(200);
    }

    @Test
    public void testOldTreTokenHeaderIsRejected() throws Exception {
        // 旧 tre-token 请求头应被拒绝（401）
        HttpRequest req = new HttpRequest("GET", "/gdl/api/getOntologies", null);
        req.addHeader("tre-token", TEST_TOKEN);
        HttpResponse resp = transport.execute(req);
        assertThat(resp.getStatusCode()).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGmlToDagEndpoint() throws Exception {
        HttpRequest req = new HttpRequest("POST", "/gdl/api/getGmlToDag", "{\"code\": \"def x = 1\"}");
        req.addHeader("gdl-token", TEST_TOKEN);
        HttpResponse resp = transport.execute(req);
        assertThat(resp.getStatusCode()).isEqualTo(200);
        // 返回的应是 DAG JSON
        assertThat(resp.getBody()).contains("nodes");
    }

    @Test
    public void testConstantsUseGdlPrefix() {
        assertThat(GdlConstants.CONF_FILE_NAME).isEqualTo("gdl.properties");
        assertThat(GdlConstants.TEMP_TABLE_PREFIX).isEqualTo("gdl_temp_");
        assertThat(GdlConstants.PROPERTY_LLM_URL).isEqualTo("gdl.llm.url");
        assertThat(GdlConstants.PROPERTY_TEMP_FILE_PATH).isEqualTo("gdl.temp.file.path");
    }

    @Test
    public void testBdosVariablesAreRemoved() {
        // Bdos* 变量已删除，resolve 应抛 Unknown variable generator
        VariableManager vm = VariableManager.getInstance();
        assertThatThrownBy(() -> vm.resolve(null, "BdosPartitionIncrementVar", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown variable generator");
        assertThatThrownBy(() -> vm.resolve(null, "BdosReadLastPartitionVar", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown variable generator");
    }
}
