package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.*;

public class HttpOperator extends LogicalOperator {
    private final String method;
    private final String url;
    private int connectionTimeout = 5000;
    private int readTimeout = 5000;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, Object> forms = new LinkedHashMap<>();
    private String body;
    private PageConfig pageConfig;
    private ResponseConfig responseConfig;
    private HttpOperator authOperator;
    private CmdDatasource targetDatasource;
    private String targetTableName;
    private boolean overwrite = false;

    public HttpOperator(String method, String url) {
        this.method = method != null ? method.toUpperCase() : "GET";
        this.url = url;
    }

    public HttpOperator(LogicalOperator upstream, String method, String url) {
        addUpstream(upstream);
        this.method = method != null ? method.toUpperCase() : "GET";
        this.url = url;
    }

    public HttpOperator connectionTimeout(int ms) {
        this.connectionTimeout = ms;
        return this;
    }

    public HttpOperator readTimeout(int ms) {
        this.readTimeout = ms;
        return this;
    }

    public HttpOperator header(Map<String, String> headers) {
        if (headers != null) {
            this.headers.putAll(headers);
        }
        return this;
    }

    public HttpOperator header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public HttpOperator form(Map<String, Object> forms) {
        if (forms != null) {
            this.forms.putAll(forms);
        }
        return this;
    }

    public HttpOperator form(String key, Object value) {
        this.forms.put(key, value);
        return this;
    }

    public HttpOperator body(String body) {
        this.body = body;
        return this;
    }

    public HttpOperator page(int pageType, int pageStart, int pageSize, int maxPage, String dataPath) {
        this.pageConfig = new PageConfig(pageType, pageStart, pageSize, maxPage, dataPath);
        return this;
    }

    public HttpOperator response(String dataPath, boolean arrayFlat, List<Map<String, Object>> columns) {
        this.responseConfig = new ResponseConfig(dataPath, arrayFlat, columns);
        return this;
    }

    public HttpOperator auth(HttpOperator authOperator) {
        this.authOperator = authOperator;
        addDependency(authOperator);
        return this;
    }

    public HttpOperator areaCode(String areaCode) {
        setAreaCode(areaCode);
        return this;
    }

    public HttpOperator to(CmdDatasource ds, String tableName) {
        this.targetDatasource = ds;
        this.targetTableName = tableName;
        this.overwrite = false;
        return this;
    }

    public HttpOperator overwriteTo(CmdDatasource ds, String tableName) {
        this.targetDatasource = ds;
        this.targetTableName = tableName;
        this.overwrite = true;
        return this;
    }

    public HttpOperator overwrite() {
        this.overwrite = true;
        return this;
    }

    public String getMethod() { return method; }
    public String getUrl() { return url; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, Object> getForms() { return forms; }
    public String getBody() { return body; }
    public PageConfig getPageConfig() { return pageConfig; }
    public ResponseConfig getResponseConfig() { return responseConfig; }
    public HttpOperator getAuthOperator() { return authOperator; }
    public CmdDatasource getTargetDatasource() { return targetDatasource; }
    public String getTargetTableName() { return targetTableName; }
    public boolean isOverwrite() { return overwrite; }

    @Override
    public String getOperatorName() {
        return "http";
    }

    @Override
    public String toString() {
        return "http(" + method + ", " + url + ")";
    }

    public static class PageConfig {
        public final int pageType;
        public final int pageStart;
        public final int pageSize;
        public final int maxPage;
        public final String dataPath;

        public PageConfig(int pageType, int pageStart, int pageSize, int maxPage, String dataPath) {
            this.pageType = pageType;
            this.pageStart = pageStart;
            this.pageSize = pageSize;
            this.maxPage = maxPage;
            this.dataPath = dataPath;
        }
    }

    public static class ResponseConfig {
        public final String dataPath;
        public final boolean arrayFlat;
        public final List<Map<String, Object>> columns;

        public ResponseConfig(String dataPath, boolean arrayFlat, List<Map<String, Object>> columns) {
            this.dataPath = dataPath;
            this.arrayFlat = arrayFlat;
            this.columns = columns != null ? columns : List.of();
        }
    }
}
