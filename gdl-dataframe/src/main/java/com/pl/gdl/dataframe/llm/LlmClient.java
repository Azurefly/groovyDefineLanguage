package com.pl.gdl.dataframe.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI-compatible Chat Completions HTTP 客户端。
 * 用于 GDL 的 llmCall 算子真实调用大模型服务（如 Ollama、OpenAI 兼容网关）。
 */
public class LlmClient implements AutoCloseable {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String url;
    private final String apiKey;

    public LlmClient(String url, String apiKey, int timeoutSeconds) {
        this.url = url;
        this.apiKey = apiKey;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        // 尊重 no_proxy 环境变量：localhost 等直连，不走代理
        ProxySelector defaultSelector = ProxySelector.getDefault();
        builder.proxySelector(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String host = uri.getHost();
                if (isNoProxy(host)) {
                    return List.of(Proxy.NO_PROXY);
                }
                if (defaultSelector != null) {
                    return defaultSelector.select(uri);
                }
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                if (defaultSelector != null) {
                    defaultSelector.connectFailed(uri, sa, ioe);
                }
            }
        });
        this.http = builder.build();
    }

    private static boolean isNoProxy(String host) {
        if (host == null) {
            return false;
        }
        String noProxy = System.getenv("no_proxy");
        if (noProxy == null) {
            noProxy = System.getenv("NO_PROXY");
        }
        if (noProxy == null || noProxy.isBlank()) {
            // 默认 localhost 直连
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        }
        String h = host.toLowerCase();
        for (String p : noProxy.split(",")) {
            p = p.trim().toLowerCase();
            if (p.isEmpty()) {
                continue;
            }
            if (p.startsWith(".")) {
                if (h.endsWith(p)) {
                    return true;
                }
            } else if (h.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 调用 chat completions，返回模型回复文本。
     *
     * @param model       模型名称
     * @param systemRole  系统角色（可为空）
     * @param userPrompt  用户提示词
     * @param params      额外参数（temperature、top_p、response_format 等）
     */
    public String chat(String model, String systemRole, String userPrompt, Map<String, Object> params) throws IOException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("stream", false);

        ArrayNode messages = mapper.createArrayNode();
        if (systemRole != null && !systemRole.isBlank()) {
            ObjectNode sys = mapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemRole);
            messages.add(sys);
        }
        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(user);
        body.set("messages", messages);

        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                String k = e.getKey();
                if ("apiKey".equals(k)) {
                    continue; // apiKey 走 header，不进 body
                }
                Object v = e.getValue();
                if (v instanceof Number n) {
                    body.put(k, n.doubleValue());
                } else if (v instanceof Boolean b) {
                    body.put(k, b);
                } else if (v != null) {
                    body.put(k, String.valueOf(v));
                }
            }
            // response_format 可能是 JSON 字符串，需要解析为对象
            Object rf = params.get("response_format");
            if (rf != null) {
                String rfStr = String.valueOf(rf).trim();
                if (rfStr.startsWith("{")) {
                    try {
                        body.set("response_format", mapper.readTree(rfStr));
                    } catch (Exception ignored) {
                        // 解析失败则保持字符串形式
                    }
                }
            }
        }

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(mapper.writeValueAsString(body), JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey);
        }

        try (Response resp = http.newCall(reqBuilder.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("LLM API 调用失败: HTTP " + resp.code() + " - " + respBody);
            }
            JsonNode root = mapper.readTree(respBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new IOException("LLM API 返回无 choices: " + respBody);
            }
            JsonNode content = choices.get(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IOException("LLM API 返回无 content: " + respBody);
            }
            return content.asText();
        }
    }

    @Override
    public void close() {
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
