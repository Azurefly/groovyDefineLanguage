package com.pl.gdl.dataframe.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.Row;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行 HttpOperator：对输入数据的每行发起 HTTP 请求，将响应追加为新列。
 *
 * <p>URL/请求头/请求体支持 {@code REF{column}} 占位符，会被替换为行数据中的列值。</p>
 */
public class HttpCallExecutor {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern REF_PATTERN = Pattern.compile("REF\\{(\\w+)\\}");

    /**
     * 执行 HTTP 调用算子。
     *
     * @param operator HTTP 调用算子
     * @param input 上游输入数据（可为空，为空时发起单次无参数请求）
     * @return 追加了响应列的数据框
     */
    public RowDataFrame execute(HttpOperator operator, RowDataFrame input) {
        String url = operator.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("HttpOperator 未配置 url");
        }

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(operator.getConnectionTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(operator.getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(operator.getReadTimeout(), TimeUnit.MILLISECONDS)
                .build();

        int rowCount = input == null ? 0 : input.rowSize();
        // 无输入时发起单次请求
        List<Row> inputRows = new ArrayList<>();
        if (rowCount == 0) {
            inputRows.add(new Row());
        } else {
            for (int i = 0; i < rowCount; i++) {
                inputRows.add(input.getRow(i));
            }
        }

        // 并发发起请求（IO 密集型，固定 10 线程）
        int concurrent = Math.min(10, Math.max(1, inputRows.size()));
        ExecutorService pool = Executors.newFixedThreadPool(concurrent);
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (Row row : inputRows) {
                final Row r = row;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return doRequest(http, operator, r);
                    } catch (Exception e) {
                        throw new RuntimeException("HTTP 调用失败: " + e.getMessage(), e);
                    }
                }, pool));
            }

            List<Map<String, Object>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(java.util.stream.Collectors.toList());

            // 构造输出：原列 + 响应列
            List<ColumnInfo> outCols = new ArrayList<>();
            if (input != null) {
                outCols.addAll(input.getColumns());
            }
            // 收集所有响应键作为新列
            java.util.Set<String> responseKeys = new java.util.LinkedHashSet<>();
            for (Map<String, Object> m : results) {
                responseKeys.addAll(m.keySet());
            }
            for (String key : responseKeys) {
                outCols.add(new ColumnInfo("http_" + key, "STRING"));
            }

            RowDataFrame output = new RowDataFrame(outCols);
            for (int i = 0; i < inputRows.size(); i++) {
                Row inRow = inputRows.get(i);
                Row outRow = new Row();
                if (input != null) {
                    for (ColumnInfo col : input.getColumns()) {
                        outRow.setValue(col.getColumnName(), inRow.getValue(col.getColumnName()));
                    }
                }
                Map<String, Object> resp = results.get(i);
                for (String key : responseKeys) {
                    Object v = resp.get(key);
                    outRow.setValue("http_" + key, v == null ? null : String.valueOf(v));
                }
                output.addRow(outRow);
            }
            return output;
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
    }

    private Map<String, Object> doRequest(OkHttpClient http, HttpOperator op, Row row) throws IOException {
        String url = substitute(op.getUrl(), row);

        Request.Builder reqBuilder = new Request.Builder().url(url);
        // 请求头（支持 REF 占位符）
        for (Map.Entry<String, String> e : op.getHeaders().entrySet()) {
            reqBuilder.header(e.getKey(), substitute(e.getValue(), row));
        }

        String method = op.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            String body = op.getBody() != null ? substitute(op.getBody(), row) : "{}";
            // 表单参数合并到 body（简化：转为 JSON）
            if (!op.getForms().isEmpty()) {
                Map<String, Object> formMap = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : op.getForms().entrySet()) {
                    Object v = e.getValue();
                    formMap.put(e.getKey(), v instanceof String ? substitute((String) v, row) : v);
                }
                body = MAPPER.writeValueAsString(formMap);
            }
            RequestBody requestBody = RequestBody.create(body, JSON);
            reqBuilder.method(method.toUpperCase(), requestBody);
        } else {
            reqBuilder.get();
        }

        try (Response resp = http.newCall(reqBuilder.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("HTTP " + resp.code() + " " + resp.message());
            }
            String respBody = resp.body() != null ? resp.body().string() : "{}";
            return parseResponse(respBody, op.getResponseConfig());
        }
    }

    /**
     * 解析响应 JSON，根据 ResponseConfig 提取数据。
     * 简化实现：支持 dataPath（点分隔路径），返回扁平化的 Map。
     */
    private Map<String, Object> parseResponse(String respBody, HttpOperator.ResponseConfig config) throws IOException {
        JsonNode root = MAPPER.readTree(respBody);
        JsonNode data = root;
        if (config != null && config.dataPath != null && !config.dataPath.isBlank()) {
            for (String part : config.dataPath.split("\\.")) {
                if (data != null) {
                    data = data.get(part);
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (data != null) {
            if (data.isObject()) {
                data.fields().forEachRemaining(e -> result.put(e.getKey(), jsonToJava(e.getValue())));
            } else if (data.isArray() && data.size() > 0) {
                // 数组取第一项展平（简化）
                JsonNode first = data.get(0);
                if (first.isObject()) {
                    first.fields().forEachRemaining(e -> result.put(e.getKey(), jsonToJava(e.getValue())));
                } else {
                    result.put("value", jsonToJava(first));
                }
            } else {
                result.put("value", jsonToJava(data));
            }
        }
        return result;
    }

    private Object jsonToJava(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.toString();
    }

    /**
     * 替换 REF{column} 占位符。
     */
    private String substitute(String template, Row row) {
        if (template == null || row == null) {
            return template;
        }
        Matcher m = REF_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String colName = m.group(1);
            Object val = null;
            try {
                val = row.getValue(colName);
            } catch (Exception ignored) {
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : String.valueOf(val)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
