package com.pl.gdl.server.client;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.http.HttpRequest;
import com.pl.gdl.server.http.HttpResponse;
import com.pl.gdl.server.http.HttpTransport;
import com.pl.gdl.server.http.UrlConnectionHttpTransport;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TreRemoteHttpClient implements TreClient {
    private final HttpTransport transport;
    private final String token;

    public TreRemoteHttpClient(String baseUrl) {
        this(new UrlConnectionHttpTransport(baseUrl), null);
    }

    public TreRemoteHttpClient(String baseUrl, String token) {
        this(new UrlConnectionHttpTransport(baseUrl), token);
    }

    public TreRemoteHttpClient(HttpTransport transport, String token) {
        if (transport == null) {
            throw new IllegalArgumentException("transport cannot be null");
        }
        this.transport = transport;
        this.token = token;
    }

    public HttpTransport getTransport() {
        return transport;
    }

    @Override
    public RegisterRsp registerOntology(String gdl) {
        Map<String, Object> payload = Map.of("gdl", gdl != null ? gdl : "");
        String resp = sendRequest("POST", "/tre/api/registerOntology", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            int status = map.containsKey("status") ? ((Number) map.get("status")).intValue() : 0;
            String msg = (String) map.get("msg");
            Object data = map.get("data");
            return new RegisterRsp(status, msg, data);
        }
        return RegisterRsp.fail("Invalid server response: " + resp);
    }

    @Override
    public RegisterRsp registerOntologies(List<String> gdls) {
        if (gdls == null || gdls.isEmpty()) {
            return RegisterRsp.fail("Empty script list");
        }
        RegisterRsp last = null;
        for (String gdl : gdls) {
            last = registerOntology(gdl);
            if (last.getStatus() == RegisterRsp.STATUS_FAILED) {
                return last;
            }
        }
        return last != null ? last : RegisterRsp.success("All registered");
    }

    @Override
    public RegisterRsp updateOntologies(List<String> gdls) {
        return registerOntologies(gdls);
    }

    @Override
    public RegisterRsp unregisterOntology(String names) {
        Map<String, Object> payload = Map.of("names", names != null ? names : "");
        String resp = sendRequest("POST", "/tre/api/unregisterOntology", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            int status = map.containsKey("status") ? ((Number) map.get("status")).intValue() : 0;
            String msg = (String) map.get("message");
            Object data = map.get("data");
            return new RegisterRsp(status, msg, data);
        }
        return RegisterRsp.fail("Invalid server response: " + resp);
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames) {
        return getOntologies(fullOntologyNames, "local");
    }

    private static String getCaseInsensitive(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k) && map.get(k) != null) {
                return String.valueOf(map.get(k));
            }
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            for (String k : keys) {
                if (String.valueOf(entry.getKey()).equalsIgnoreCase(k) && entry.getValue() != null) {
                    return String.valueOf(entry.getValue());
                }
            }
        }
        return null;
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode) {
        StringBuilder query = new StringBuilder();
        if (fullOntologyNames != null && !fullOntologyNames.isBlank()) {
            query.append("fullOntologyNames=").append(URLEncoder.encode(fullOntologyNames, StandardCharsets.UTF_8)).append("&");
        }
        if (areaCode != null && !areaCode.isBlank()) {
            query.append("areaCode=").append(URLEncoder.encode(areaCode, StandardCharsets.UTF_8));
        }

        String resp = sendRequest("GET", "/tre/api/getOntologies", query.toString(), null);
        Object parsed = new JsonSlurper().parseText(resp);
        List<OntoInfoRsp> result = new ArrayList<>();
        if (parsed instanceof Map<?, ?> map && map.get("data") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    OntoInfoRsp info = new OntoInfoRsp();
                    info.setOId(getCaseInsensitive(m, "OId", "oId", "oid"));
                    info.setOName(getCaseInsensitive(m, "OName", "oName", "oname"));
                    info.setODesc(getCaseInsensitive(m, "ODesc", "oDesc", "odesc"));
                    info.setOTable(getCaseInsensitive(m, "OTable", "oTable", "otable"));
                    info.setOAuthor(getCaseInsensitive(m, "OAuthor", "oAuthor", "oauthor"));
                    info.setClassName(getCaseInsensitive(m, "className", "classname"));
                    info.setVersion(getCaseInsensitive(m, "version"));

                    if (m.get("fields") instanceof List<?> fList) {
                        for (Object fo : fList) {
                            if (fo instanceof Map<?, ?> fm) {
                                info.getFields().add(new OntoInfoRsp.OntoField(
                                        getCaseInsensitive(fm, "fieldName", "fieldname"),
                                        getCaseInsensitive(fm, "columnName", "columnname"),
                                        getCaseInsensitive(fm, "remarks")
                                ));
                            }
                        }
                    }
                    result.add(info);
                }
            }
        }
        return result;
    }

    @Override
    public String startTask(String gdlScript, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", gdlScript);
        payload.put("params", params != null ? params : Map.of());

        String resp = sendRequest("POST", "/tre/api/startTask", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map && map.containsKey("taskId")) {
            return (String) map.get("taskId");
        }
        throw new RuntimeException("Failed to start task: " + resp);
    }

    @Override
    public TaskResult getTaskResult(String taskId) {
        String query = "taskId=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        String resp = sendRequest("GET", "/tre/api/getTaskResult", query, null);
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            TaskResult tr = new TaskResult();
            tr.setTaskId((String) map.get("taskId"));
            tr.setStatus((String) map.get("status"));
            tr.setMessage((String) map.get("msg"));
            return tr;
        }
        return null;
    }

    @Override
    public String getTsmlToDag(String gdlScript) {
        Map<String, Object> payload = Map.of("code", gdlScript != null ? gdlScript : "");
        return sendRequest("POST", "/tre/api/getTsmlToDag", null, JsonOutput.toJson(payload));
    }

    // --- Low-level HTTP Transport Delegation ---

    private String sendRequest(String method, String path, String query, String jsonBody) {
        try {
            HttpRequest req = new HttpRequest(method, path, jsonBody);
            req.setQuery(query);
            if (token != null && !token.isBlank()) {
                req.addHeader("tre-token", token);
            }
            if (jsonBody != null && !jsonBody.isBlank()) {
                req.addHeader("Content-Type", "application/json; charset=UTF-8");
            }

            HttpResponse resp = transport.execute(req);
            if (!resp.isSuccessful()) {
                throw new RuntimeException("HTTP request failed with status: " + resp.getStatusCode() + ", body: " + resp.getBody());
            }
            return resp.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Remote HTTP call failed on " + path + ": " + e.getMessage(), e);
        }
    }
}
