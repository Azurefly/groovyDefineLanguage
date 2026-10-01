package com.pl.gdl.server.http;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequest implements Serializable {
    private String method = "GET";
    private String path = "/";
    private String query = "";
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body = "";

    public HttpRequest() {}

    public HttpRequest(String method, String path, String body) {
        this.method = method;
        this.path = path;
        this.body = body != null ? body : "";
    }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

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
}
