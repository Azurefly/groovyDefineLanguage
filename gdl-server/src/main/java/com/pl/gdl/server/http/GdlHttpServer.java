package com.pl.gdl.server.http;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.client.TreClient;
import com.pl.gdl.server.client.TreClientImpl;
import com.pl.gdl.server.config.ServerConfig;
import com.pl.gdl.server.mcp.McpTool;
import com.pl.gdl.server.mcp.McpToolRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class GdlHttpServer {
    private static final Logger log = LoggerFactory.getLogger(GdlHttpServer.class);

    private final ServerConfig config;
    private final TreClient treClient;
    private final McpToolRegistry mcpRegistry;
    private HttpServer server;
    private boolean running = false;

    public GdlHttpServer() {
        this(new ServerConfig());
    }

    public GdlHttpServer(ServerConfig config) {
        this(config, new TreClientImpl());
    }

    public GdlHttpServer(ServerConfig config, TreClient treClient) {
        this.config = config != null ? config : new ServerConfig();
        this.treClient = treClient != null ? treClient : new TreClientImpl();
        this.mcpRegistry = new McpToolRegistry(this.treClient);
    }

    public synchronized void start() throws IOException {
        if (running) return;

        try {
            InetSocketAddress address = new InetSocketAddress(config.getHost(), config.getPort());
            this.server = HttpServer.create(address, 0);
            this.server.setExecutor(Executors.newFixedThreadPool(config.getWorkerThreads()));

            server.createContext("/tre/api/health", new HealthHandler());
            server.createContext("/tre/api/getOntologies", new GetOntologiesHandler());
            server.createContext("/tre/api/registerOntology", new RegisterOntologyHandler());
            server.createContext("/tre/api/unregisterOntology", new UnregisterOntologyHandler());
            server.createContext("/tre/api/startTask", new StartTaskHandler());
            server.createContext("/tre/api/getTaskResult", new GetTaskResultHandler());
            server.createContext("/tre/api/getTsmlToDag", new GetTsmlToDagHandler());
            server.createContext("/tre/mcp/service", new McpServiceHandler());

            this.server.start();
            log.info("GDL HTTP Server listening on http://{}:{}", config.getHost(), getPort());
        } catch (java.net.SocketException se) {
            log.warn("Socket bind not permitted in current sandbox/restricted environment. In-process dispatch will be used: {}", se.getMessage());
        }
        this.running = true;
    }

    public synchronized void stop() {
        if (!running) return;
        if (this.server != null) {
            this.server.stop(0);
        }
        this.running = false;
        log.info("GDL HTTP Server stopped");
    }

    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return config.getPort();
    }

    public boolean isRunning() {
        return running;
    }

    public TreClient getTreClient() {
        return treClient;
    }

    public McpToolRegistry getMcpRegistry() {
        return mcpRegistry;
    }

    // --- Direct In-Process Request Dispatcher ---

    public HttpResponse handleDirect(HttpRequest req) {
        String path = req.getPath();
        if (path.contains("?")) {
            String[] parts = path.split("\\?", 2);
            path = parts[0];
            req.setPath(path);
            if (req.getQuery() == null || req.getQuery().isBlank()) {
                req.setQuery(parts[1]);
            }
        }

        // Health check always public
        if ("/tre/api/health".equals(path)) {
            return jsonResponse(200, Map.of(
                    "status", "UP",
                    "service", "GDL / TRE Engine",
                    "version", "1.0.0-GA",
                    "timestamp", System.currentTimeMillis()
            ));
        }

        // Check authentication for other endpoints
        if (config.isRequireToken()) {
            String token = req.getHeader("tre-token");
            if (token == null || !token.equals(config.getToken())) {
                HttpResponse res = new HttpResponse(401, JsonOutput.toJson(Map.of(
                        "code", -1,
                        "msg", "Unauthorized: Invalid or missing 'tre-token' header",
                        "success", false
                )));
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return res;
            }
        }

        try {
            if ("/tre/api/getOntologies".equals(path)) {
                if (!"GET".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                Map<String, String> query = parseQueryParams(req.getQuery());
                String names = query.get("fullOntologyNames");
                String areaCode = query.getOrDefault("areaCode", "local");

                List<OntoInfoRsp> ontos = treClient.getOntologies(names, areaCode);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("code", 0);
                resp.put("msg", "操作成功");
                resp.put("data", ontos);
                resp.put("success", true);
                return jsonResponse(200, resp);
            } else if ("/tre/api/registerOntology".equals(path)) {
                if (!"POST".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                String gdlContent = req.getBody();
                try {
                    Object parsed = new JsonSlurper().parseText(req.getBody());
                    if (parsed instanceof Map<?, ?> map && map.containsKey("gdl")) {
                        gdlContent = String.valueOf(map.get("gdl"));
                    }
                } catch (Exception ignored) {}

                RegisterRsp regRsp = treClient.registerOntology(gdlContent);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("code", regRsp.getStatus() == RegisterRsp.STATUS_SUCCESS ? 0 : -1);
                resp.put("msg", regRsp.getMessage());
                resp.put("status", regRsp.getStatus());
                resp.put("data", regRsp.getData());
                resp.put("success", regRsp.getStatus() == RegisterRsp.STATUS_SUCCESS);
                return jsonResponse(regRsp.getStatus() == RegisterRsp.STATUS_SUCCESS ? 200 : 400, resp);
            } else if ("/tre/api/unregisterOntology".equals(path)) {
                if (!"POST".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                String names = req.getBody();
                try {
                    Object parsed = new JsonSlurper().parseText(req.getBody());
                    if (parsed instanceof Map<?, ?> map && map.containsKey("names")) {
                        names = String.valueOf(map.get("names"));
                    }
                } catch (Exception ignored) {}

                RegisterRsp unregRsp = treClient.unregisterOntology(names);
                return jsonResponse(200, unregRsp);
            } else if ("/tre/api/startTask".equals(path)) {
                if (!"POST".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                String code = null;
                Map<String, Object> params = new LinkedHashMap<>();
                try {
                    Object parsed = new JsonSlurper().parseText(req.getBody());
                    if (parsed instanceof Map<?, ?> map) {
                        code = (String) map.get("code");
                        Object p = map.get("params");
                        if (p instanceof Map<?, ?> pm) {
                            pm.forEach((k, v) -> params.put(String.valueOf(k), v));
                        } else if (p instanceof String pstr && !pstr.isBlank()) {
                            for (String pair : pstr.split(";")) {
                                String[] kv = pair.split("=", 2);
                                if (kv.length == 2) params.put(kv[0].trim(), kv[1].trim());
                            }
                        }
                    }
                } catch (Exception e) {
                    code = req.getBody();
                }

                if (code == null || code.isBlank()) {
                    return jsonResponse(400, Map.of("code", -1, "msg", "GDL script code cannot be empty"));
                }

                String taskId = treClient.startTask(code, params);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("code", 0);
                resp.put("msg", "任务提交成功");
                resp.put("taskId", taskId);
                resp.put("status", "SUBMITTED");
                resp.put("success", true);
                return jsonResponse(200, resp);
            } else if ("/tre/api/getTaskResult".equals(path)) {
                if (!"GET".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                Map<String, String> query = parseQueryParams(req.getQuery());
                String taskId = query.get("taskId");
                if (taskId == null || taskId.isBlank()) {
                    return jsonResponse(400, Map.of("code", -1, "msg", "taskId is required"));
                }

                TaskResult tr = treClient.getTaskResult(taskId);
                if (tr == null) {
                    return jsonResponse(404, Map.of("code", -1, "msg", "Task not found: " + taskId));
                }

                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("code", 0);
                resp.put("msg", "操作成功");
                resp.put("status", tr.getStatus());
                resp.put("taskId", tr.getTaskId());
                resp.put("result", tr.getResult());
                resp.put("success", true);
                return jsonResponse(200, resp);
            } else if ("/tre/api/getTsmlToDag".equals(path)) {
                if (!"POST".equalsIgnoreCase(req.getMethod())) {
                    return jsonResponse(405, Map.of("code", -1, "msg", "Method Not Allowed"));
                }
                String code = req.getBody();
                try {
                    Object parsed = new JsonSlurper().parseText(req.getBody());
                    if (parsed instanceof Map<?, ?> map && map.containsKey("code")) {
                        code = (String) map.get("code");
                    }
                } catch (Exception ignored) {}

                String dagJson = treClient.getTsmlToDag(code);
                HttpResponse res = new HttpResponse(200, dagJson);
                res.addHeader("Content-Type", "application/json; charset=UTF-8");
                return res;
            } else if ("/tre/mcp/service".equals(path)) {
                if ("GET".equalsIgnoreCase(req.getMethod())) {
                    List<Map<String, Object>> toolsList = new ArrayList<>();
                    for (McpTool tool : mcpRegistry.getAllTools()) {
                        toolsList.add(Map.of(
                                "name", tool.getName(),
                                "description", tool.getDescription(),
                                "inputSchema", tool.getInputSchema()
                        ));
                    }
                    return jsonResponse(200, Map.of("tools", toolsList));
                }

                if ("POST".equalsIgnoreCase(req.getMethod())) {
                    try {
                        Object parsed = new JsonSlurper().parseText(req.getBody());
                        if (parsed instanceof Map<?, ?> mcpReq) {
                            String method = (String) mcpReq.get("method");
                            if ("tools/list".equals(method)) {
                                List<Map<String, Object>> toolsList = new ArrayList<>();
                                for (McpTool tool : mcpRegistry.getAllTools()) {
                                    toolsList.add(Map.of(
                                            "name", tool.getName(),
                                            "description", tool.getDescription(),
                                            "inputSchema", tool.getInputSchema()
                                    ));
                                }
                                return jsonResponse(200, Map.of("result", Map.of("tools", toolsList)));
                            }

                            String toolName = null;
                            Map<String, Object> arguments = Map.of();

                            if ("tools/call".equals(method) && mcpReq.get("params") instanceof Map<?, ?> p) {
                                toolName = (String) p.get("name");
                                if (p.get("arguments") instanceof Map<?, ?> args) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> castArgs = (Map<String, Object>) args;
                                    arguments = castArgs;
                                }
                            } else if (mcpReq.containsKey("tool")) {
                                toolName = (String) mcpReq.get("tool");
                                if (mcpReq.get("arguments") instanceof Map<?, ?> args) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> castArgs = (Map<String, Object>) args;
                                    arguments = castArgs;
                                }
                            }

                            if (toolName != null) {
                                Object toolResult = mcpRegistry.executeTool(toolName, arguments);
                                return jsonResponse(200, Map.of("result", toolResult, "isError", false));
                            }
                        }
                    } catch (Exception e) {
                        return jsonResponse(500, Map.of("isError", true, "error", e.getMessage()));
                    }
                }
                return jsonResponse(400, Map.of("error", "Invalid MCP request"));
            }

            return jsonResponse(404, Map.of("code", -1, "msg", "Not Found: " + path));
        } catch (Exception e) {
            log.error("Internal server error", e);
            return jsonResponse(500, Map.of("code", -1, "msg", "Internal server error: " + e.getMessage()));
        }
    }

    private static HttpResponse jsonResponse(int status, Object data) {
        String json = (data instanceof String str && (str.startsWith("{") || str.startsWith("[")))
                ? (String) data : JsonOutput.toJson(data);
        HttpResponse res = new HttpResponse(status, json);
        res.addHeader("Content-Type", "application/json; charset=UTF-8");
        return res;
    }

    // --- Helper Methods ---

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            } else if (kv.length == 1) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }

    // --- Standard Java HttpExchange Handlers (for physical socket listen) ---

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class GetOntologiesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class RegisterOntologyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class UnregisterOntologyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class StartTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class GetTaskResultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class GetTsmlToDagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private class McpServiceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            HttpRequest req = toHttpRequest(exchange);
            HttpResponse res = handleDirect(req);
            fromHttpResponse(exchange, res);
        }
    }

    private HttpRequest toHttpRequest(HttpExchange exchange) throws IOException {
        HttpRequest req = new HttpRequest();
        req.setMethod(exchange.getRequestMethod());
        req.setPath(exchange.getRequestURI().getPath());
        req.setQuery(exchange.getRequestURI().getQuery());
        exchange.getRequestHeaders().forEach((k, v) -> {
            if (v != null && !v.isEmpty()) req.addHeader(k, v.get(0));
        });
        req.setBody(readBody(exchange));
        return req;
    }

    private void fromHttpResponse(HttpExchange exchange, HttpResponse res) throws IOException {
        for (Map.Entry<String, String> h : res.getHeaders().entrySet()) {
            exchange.getResponseHeaders().set(h.getKey(), h.getValue());
        }
        byte[] bytes = res.getBody().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(res.getStatusCode(), bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
