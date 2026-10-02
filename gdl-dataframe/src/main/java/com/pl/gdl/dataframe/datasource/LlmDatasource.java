package com.pl.gdl.dataframe.datasource;

public class LlmDatasource extends CmdDatasource {
    private String url;
    private int concurrent = 10;
    private int timeout = 1800; // seconds

    public LlmDatasource() {
        super("llm-default");
    }

    public LlmDatasource(String url, int concurrent) {
        this(url, concurrent, 1800);
    }

    public LlmDatasource(String url, int concurrent, int timeoutSeconds) {
        super("llm-custom");
        this.url = url;
        this.concurrent = concurrent > 0 ? concurrent : 10;
        this.timeout = timeoutSeconds > 0 ? timeoutSeconds : 1800;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getConcurrent() { return concurrent; }
    public void setConcurrent(int concurrent) { this.concurrent = concurrent; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    @Override
    public String getDatasourceType() {
        return "LLM";
    }

    @Override
    public String toString() {
        return "LlmDatasource{url='" + url + "', concurrent=" + concurrent + ", areaCode='" + areaCode + "'}";
    }
}
