package com.pl.gdl.server.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 基于 JDK {@link HttpURLConnection} 的 {@link HttpTransport} 实现，
 * 用于 {@code TreRemoteHttpClient} 调用远端 GDL / TRE 服务。
 *
 * <p><b>资源与安全约束：</b></p>
 * <ul>
 *   <li>响应体读取上限 10MB，超限抛出 {@link IOException}，防止远端返回超大
 *       响应耗尽客户端内存；</li>
 *   <li>请求完成后在 {@code finally} 中调用 {@code disconnect()} 释放底层连接。</li>
 * </ul>
 */
public class UrlConnectionHttpTransport implements HttpTransport {
    /** 响应体读取上限：10MB。 */
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    private final String baseUrl;
    private int connectTimeout = 10000;
    private int readTimeout = 30000;

    /**
     * 构造传输层。
     *
     * @param baseUrl 远端服务基地址（如 {@code http://host:port}），不能为空
     * @throws IllegalArgumentException baseUrl 为空时抛出
     */
    public UrlConnectionHttpTransport(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl cannot be empty");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 设置连接超时（毫秒），默认 10000。
     *
     * @param connectTimeout 连接超时毫秒数
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * 设置读取超时（毫秒），默认 30000。
     *
     * @param readTimeout 读取超时毫秒数
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    public HttpResponse execute(HttpRequest request) throws Exception {
        String fullUrl = baseUrl + request.getPath();
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            fullUrl += "?" + request.getQuery();
        }

        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod(request.getMethod());
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setDoInput(true);

            for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }

            if (request.getBody() != null && !request.getBody().isBlank()) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(request.getBody().getBytes(StandardCharsets.UTF_8));
                }
            }

            int statusCode = conn.getResponseCode();
            HttpResponse response = new HttpResponse();
            response.setStatusCode(statusCode);

            for (Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                    response.addHeader(entry.getKey(), entry.getValue().get(0));
                }
            }

            InputStream is = (statusCode >= 200 && statusCode < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (is != null) {
                try (is) {
                    response.setBody(new String(readBounded(is, MAX_RESPONSE_BYTES), StandardCharsets.UTF_8));
                }
            }

            return response;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 有上限地读取输入流全部字节。
     *
     * @param is       输入流
     * @param maxBytes 允许的最大字节数
     * @return 读取到的字节
     * @throws IOException 读取失败，或数据量超过 {@code maxBytes} 时抛出
     */
    private static byte[] readBounded(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(8192, maxBytes));
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IOException("Response body exceeds the limit of " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
