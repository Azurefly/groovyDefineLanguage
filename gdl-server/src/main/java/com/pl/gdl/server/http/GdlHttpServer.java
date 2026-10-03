package com.pl.gdl.server.http;

import com.pl.gdl.common.exception.OntologyValidationException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * GDL / TRE 引擎的 HTTP 服务端。
 *
 * <p>同时承担两类职责：</p>
 * <ul>
 *   <li>基于 JDK 内置 {@code HttpServer} 的物理 socket 监听，对外提供
 *       {@code /tre/api/*} REST 接口与 {@code /tre/mcp/service} MCP 接口；</li>
 *   <li>通过 {@link #handleDirect(HttpRequest)} 提供进程内请求分发，
 *       供 {@link InProcessHttpTransport} 在无法绑定 socket 的受限环境下使用。</li>
 * </ul>
 *
 * <p>除 {@code /tre/api/health} 健康检查外，所有接口默认要求请求头
 * {@code tre-token} 与配置一致（见 {@link ServerConfig}），否则返回 401。</p>
 */
public class GdlHttpServer {
    private static final Logger log = LoggerFactory.getLogger(GdlHttpServer.class);

    private final ServerConfig config;
    private final TreClient treClient;
    private final McpToolRegistry mcpRegistry;
    private HttpServer server;
    private ExecutorService executor;
    private boolean running = false;

    /** 使用默认配置构造服务端。 */
    public GdlHttpServer() {
        this(new ServerConfig());
    }

    /**
     * 使用指定配置构造服务端，任务执行使用默认的本地 {@link TreClientImpl}。
     *
     * @param config 服务配置，{@code null} 时使用默认配置
     */
    public GdlHttpServer(ServerConfig config) {
        this(config, new TreClientImpl());
    }

    /**
     * 使用指定配置与任务客户端构造服务端。
     *
     * @param config    服务配置，{@code null} 时使用默认配置
     * @param treClient 任务执行客户端，{@code null} 时使用默认的本地实现
     */
    public GdlHttpServer(ServerConfig config, TreClient treClient) {
        this.config = config != null ? config : new ServerConfig();
        this.treClient = treClient != null ? treClient : new TreClientImpl();
        this.mcpRegistry = new McpToolRegistry(this.treClient);
    }

    /**
     * 启动 HTTP 服务。
     *
     * <p>创建 {@code HttpServer} 并绑定配置的地址端口，注册全部接口上下文后开始监听。
     * 若当前环境不允许绑定 socket（如受限沙箱），会记录警告并退化为仅支持
     * {@link #handleDirect(HttpRequest)} 的进程内分发模式，但仍标记为运行中。</p>
     *
     * @throws IOException 创建 HttpServer 失败时抛出（socket 绑定被拒绝除外）
     */
    public synchronized void start() throws IOException {
        if (running) return;

        try {
            InetSocketAddress address = new InetSocketAddress(config.getHost(), config.getPort());
            this.server = HttpServer.create(address, 0);
            this.executor = Executors.newFixedThreadPool(config.getWorkerThreads());
            this.server.setExecutor(this.executor);

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

    /**
     * 停止 HTTP 服务并释放全部资源。
     *
     * <p>停止顺序：先停止 {@code HttpServer} 不再接受新请求，再关闭请求处理线程池
     * （{@code shutdownNow} + 等待终止），最后若任务客户端为 {@link TreClientImpl}
     * 则关闭其任务后台线程池。</p>
     */
    public synchronized void stop() {
        if (!running) return;
        if (this.server != null) {
            this.server.stop(0);
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            try {
                if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("HTTP request executor did not terminate within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for HTTP request executor termination");
            } finally {
                this.executor = null;
            }
        }
        if (this.treClient instanceof TreClientImpl impl) {
            impl.close();
        }
        this.running = false;
        log.info("GDL HTTP Server stopped");
    }

    /**
     * 返回服务实际监听的端口。
     *
     * <p>若已成功绑定 socket，返回 socket 的实际端口；否则返回配置中的端口
     * （如配置端口为 0 时的自动分配端口在未绑定成功前无法获知）。</p>
     *
     * @return 实际监听端口
     */
    public int getPort() {
        if (server != null) {
            return server.getAddress().getPort();
        }
        return config.getPort();
    }

    /**
     * 服务是否处于运行中（包含退化为进程内分发模式的情况）。
     *
     * @return 运行中返回 {@code true}
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 返回服务使用的任务执行客户端。
     *
     * @return {@link TreClient} 实例
     */
    public TreClient getTreClient() {
        return treClient;
    }

    /**
     * 返回服务使用的 MCP 工具注册表。
     *
     * @return {@link McpToolRegistry} 实例
     */
    public McpToolRegistry getMcpRegistry() {
        return mcpRegistry;
    }

    // --- Direct In-Process Request Dispatcher ---

    /**
     * 进程内请求分发入口：不经过 socket，直接按路径路由到各接口逻辑。
     *
     * <p>处理流程：健康检查接口直接放行；其余接口先做 token 鉴权
     * （{@code tre-token} 请求头与配置一致，否则 401），再按路径分发到
     * 本体注册/查询、任务提交/查询、TSML 转 DAG、MCP 服务等分支。</p>
     *
     * @param req 进程内请求
     * @return 进程内响应
     */
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
                } catch (Exception e) { log.debug("registerOntology: request body is not JSON, treating as raw GDL content: {}", e.toString()); }

                // 本体校验失败属于客户端错误，返回 400 而非兜底 500
                RegisterRsp regRsp;
                try {
                    regRsp = treClient.registerOntology(gdlContent);
                } catch (OntologyValidationException ve) {
                    log.warn("registerOntology validation failed: {}", ve.getMessage());
                    return jsonResponse(400, Map.of("code", -1, "msg", ve.getMessage(), "success", false));
                }
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
                } catch (Exception e) { log.debug("unregisterOntology: request body is not JSON, treating as raw names: {}", e.toString()); }

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
                } catch (Exception e) { log.debug("getTsmlToDag: request body is not JSON, treating as raw GDL code: {}", e.toString()); }

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

    /** 请求体大小上限：10MB（与客户端 TreRemoteHttpClient 的响应上限对称），防止 GB 级 body 耗尽堆内存。 */
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            // 分块读取并强制上限，避免 readAllBytes 无限制分配
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(chunk)) != -1) {
                total += n;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("请求体超过上限 " + MAX_BODY_BYTES + " 字节，已拒绝");
                }
                buf.write(chunk, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
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
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    /**
     * 统一的请求处理入口：请求体超限（IOException）时返回 413，避免未处理的 IOException 导致连接挂起。
     * 各 Handler 应调用此方法而非直接调用 toHttpRequest。
     */
    @FunctionalInterface
    private interface RequestHandler {
        void handle(HttpRequest req) throws IOException;
    }

    private void handleWithBodyLimit(HttpExchange exchange, RequestHandler handler) throws IOException {
        final HttpRequest req;
        try {
            req = toHttpRequest(exchange);
        } catch (IOException e) {
            sendError(exchange, 413, e.getMessage() != null ? e.getMessage() : "请求体读取失败");
            return;
        }
        handler.handle(req);
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String body = "{\"code\":" + statusCode + ",\"msg\":\""
                + message.replace("\"", "'") + "\",\"success\":false}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private class GetOntologiesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class RegisterOntologyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class UnregisterOntologyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class StartTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class GetTaskResultHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class GetTsmlToDagHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
        }
    }

    private class McpServiceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            handleWithBodyLimit(exchange, req -> {
                HttpResponse res = handleDirect(req);
                fromHttpResponse(exchange, res);
            });
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
