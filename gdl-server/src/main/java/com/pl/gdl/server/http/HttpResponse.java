package com.pl.gdl.server.http;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse implements Serializable {
    private int statusCode = 200;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";

    public HttpResponse() {}

    public HttpResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body != null ? body : "";
    }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers != null ? headers : new LinkedHashMap<>();
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public String getHeader(String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body != null ? body : ""; }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
