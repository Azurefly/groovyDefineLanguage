package com.pl.gdl.server.client;

import com.pl.gdl.common.enums.TaskStatus;
import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.server.http.HttpRequest;
import com.pl.gdl.server.http.HttpResponse;
import com.pl.gdl.server.http.HttpTransport;
import com.pl.gdl.server.http.UrlConnectionHttpTransport;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * {@link GdlEngineClient} 的远程 HTTP 实现，通过 {@link HttpTransport} 调用远端
 * GDL 引擎远程服务的 {@code /gdl/api/*} 接口。
 *
 * <p>远端接口返回的 JSON 字段存在历史兼容差异（如 {@code msg}/{@code message}、
 * {@code result}/{@code data}），本类在解析时做了兼容处理；所有从 JSON 取出的
 * 字符串字段均使用安全转换，绝不直接做 {@code (String)} 强转。</p>
 */
public class GdlHttpEngineClient implements GdlEngineClient {
    private static final Logger log = LoggerFactory.getLogger(GdlHttpEngineClient.class);

    private final HttpTransport transport;
    private final String token;

    public GdlHttpEngineClient(String baseUrl) {
        this(new UrlConnectionHttpTransport(baseUrl), null);
    }

    public GdlHttpEngineClient(String baseUrl, String token) {
        this(new UrlConnectionHttpTransport(baseUrl), token);
    }

    /**
     * 使用指定传输层构造客户端（不携带鉴权 token）。
     *
     * <p>主要用于测试未鉴权场景或对接本身不要求鉴权的服务端。</p>
     *
     * @param transport HTTP 传输层实现，不能为 {@code null}
     */
    public GdlHttpEngineClient(HttpTransport transport) {
        this(transport, null);
    }

    public GdlHttpEngineClient(HttpTransport transport, String token) {
        if (transport == null) {
            throw new IllegalArgumentException("transport cannot be null");
        }
        this.transport = transport;
        this.token = token;
    }

    /**
     * 返回底层 HTTP 传输层。
     *
     * @return 构造时传入的 {@link HttpTransport}
     */
    public HttpTransport getTransport() {
        return transport;
    }

    @Override
    public RegisterRsp registerOntology(String gdl) {
        Map<String, Object> payload = Map.of("gdl", gdl != null ? gdl : "");
        String resp = sendRequest("POST", "/gdl/api/registerOntology", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            int status = map.containsKey("status") ? ((Number) map.get("status")).intValue() : 0;
            String msg = firstNonBlank(stringValue(map.get("msg")), stringValue(map.get("message")));
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
        String resp = sendRequest("POST", "/gdl/api/unregisterOntology", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            int status = map.containsKey("status") ? ((Number) map.get("status")).intValue() : 0;
            String msg = firstNonBlank(stringValue(map.get("msg")), stringValue(map.get("message")));
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

        String resp = sendRequest("GET", "/gdl/api/getOntologies", query.toString(), null);
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

        String resp = sendRequest("POST", "/gdl/api/startTask", null, JsonOutput.toJson(payload));
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map && map.containsKey("taskId")) {
            return stringValue(map.get("taskId"));
        }
        throw new RuntimeException("Failed to start task: " + resp);
    }

    @Override
    public TaskResult getTaskResult(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId cannot be null or blank");
        }
        String query = "taskId=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        String resp = sendRequest("GET", "/gdl/api/getTaskResult", query, null);
        Object parsed = new JsonSlurper().parseText(resp);
        if (parsed instanceof Map<?, ?> map) {
            TaskResult tr = new TaskResult();
            tr.setTaskId(stringValue(map.get("taskId")));
            tr.setStatus(parseTaskStatus(stringValue(map.get("status"))));
            tr.setMessage(firstNonBlank(stringValue(map.get("msg")), stringValue(map.get("message"))));
            // 兼容历史字段名：result 为主，data 为兼容
            Object rawResult = map.get("result");
            if (rawResult == null) {
                rawResult = map.get("data");
            }
            tr.setResult(parseAreaResults(rawResult));
            return tr;
        }
        return null;
    }

    @Override
    public String getGmlToDag(String gdlScript) {
        Map<String, Object> payload = Map.of("code", gdlScript != null ? gdlScript : "");
        return sendRequest("POST", "/gdl/api/getGmlToDag", null, JsonOutput.toJson(payload));
    }

    // --- Low-level HTTP Transport Delegation ---

    private String sendRequest(String method, String path, String query, String jsonBody) {
        try {
            HttpRequest req = new HttpRequest(method, path, jsonBody);
            req.setQuery(query);
            if (token != null && !token.isBlank()) {
                req.addHeader("gdl-token", token);
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

    // --- JSON 解析辅助方法 ---

    /**
     * 安全地将 JSON 解析出的任意对象转为字符串；{@code null} 保持 {@code null}，
     * 避免对非字符串类型做 {@code (String)} 强转导致 {@link ClassCastException}。
     */
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** 返回首个非空白字符串，若全为空则返回 {@code null}。 */
    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    /**
     * 安全解析任务状态字符串。
     *
     * <p>解析失败（未知状态或为空）时记录 debug 日志并回退为 {@link TaskStatus#PENDING}，
     * 而不是抛出异常中断查询。</p>
     */
    private static TaskStatus parseTaskStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return TaskStatus.PENDING;
        }
        try {
            return TaskStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.debug("Unknown task status '{}', fallback to PENDING", rawStatus);
            return TaskStatus.PENDING;
        }
    }

    /**
     * 将 {@code result}/{@code data} 字段解析为分区域结果列表。
     *
     * @param raw JSON 中的原始值（期望为对象数组）
     * @return 解析出的 {@link TaskResult.AreaResult} 列表；非法输入返回空列表
     */
    private static List<TaskResult.AreaResult> parseAreaResults(Object raw) {
        List<TaskResult.AreaResult> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            TaskResult.AreaResult ar = new TaskResult.AreaResult();
            ar.setAreaCode(stringValue(m.get("areaCode")));
            String code = stringValue(m.get("code"));
            ar.setCode(code != null ? code : "GDL_2000");
            String msg = firstNonBlank(stringValue(m.get("msg")), stringValue(m.get("message")));
            ar.setMsg(msg != null ? msg : "成功");
            ar.setData(parseDataRows(m.get("data")));
            ar.setMetadata(parseMetadata(m.get("metadata")));
            out.add(ar);
        }
        return out;
    }

    /** 解析分区域结果中的数据行（每行为一个字段名到值的映射）。 */
    private static List<Map<String, Object>> parseDataRows(Object raw) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return rows;
        }
        for (Object row : list) {
            if (row instanceof Map<?, ?> rm) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                rm.forEach((k, v) -> mapped.put(String.valueOf(k), v));
                rows.add(mapped);
            }
        }
        return rows;
    }

    /** 解析分区域结果中的列元数据。 */
    private static List<ColumnInfo> parseMetadata(Object raw) {
        List<ColumnInfo> metadata = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return metadata;
        }
        for (Object col : list) {
            if (col instanceof Map<?, ?> cm) {
                ColumnInfo ci = new ColumnInfo();
                ci.setColumnName(stringValue(cm.get("columnName")));
                String dataTypeName = stringValue(cm.get("dataTypeName"));
                if (dataTypeName != null && !dataTypeName.isBlank()) {
                    ci.setDataTypeName(dataTypeName);
                }
                String remarks = stringValue(cm.get("remarks"));
                ci.setRemarks(remarks != null ? remarks : "");
                metadata.add(ci);
            }
        }
        return metadata;
    }
}
