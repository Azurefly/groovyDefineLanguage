package com.pl.gdl.server.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class UrlConnectionHttpTransport implements HttpTransport {
    private final String baseUrl;
    private int connectTimeout = 10000;
    private int readTimeout = 30000;

    public UrlConnectionHttpTransport(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl cannot be empty");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

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
                response.setBody(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        return response;
    }
}
