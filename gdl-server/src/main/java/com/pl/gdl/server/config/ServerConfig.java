package com.pl.gdl.server.config;

import java.io.Serializable;

public class ServerConfig implements Serializable {
    private int port = 8080;
    private String host = "0.0.0.0";
    private String token = null; // null means no token verification required
    private boolean requireToken = false;
    private int workerThreads = 16;

    public ServerConfig() {}

    public ServerConfig(int port) {
        this.port = port;
    }

    public ServerConfig(int port, String token) {
        this.port = port;
        this.token = token;
        this.requireToken = (token != null && !token.isBlank());
    }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getToken() { return token; }
    public void setToken(String token) {
        this.token = token;
        this.requireToken = (token != null && !token.isBlank());
    }

    public boolean isRequireToken() { return requireToken; }
    public void setRequireToken(boolean requireToken) { this.requireToken = requireToken; }

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
}
