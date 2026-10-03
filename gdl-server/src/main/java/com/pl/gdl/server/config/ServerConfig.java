package com.pl.gdl.server.config;

import java.io.Serializable;

/**
 * GDL 引擎 HTTP 服务的启动配置。
 *
 * <p><b>安全语义（鉴权默认开启）：</b>自本版本起 {@code requireToken} 默认值为
 * {@code true}，遵循"默认安全"原则——除非调用方显式关闭，否则所有非健康检查接口都要求
 * 请求头 {@code gdl-token} 与配置的 token 一致，否则返回 401。</p>
 *
 * <ul>
 *   <li>{@code new ServerConfig()} / {@code new ServerConfig(port)}：鉴权默认开启，但 token
 *       为 {@code null}，实际效果等价于"开启但无可用 token"（任何业务请求都会被 401 拒绝）。
 *       如需以开放模式运行，必须显式调用 {@link #setRequireToken(boolean)}{@code (false)}。</li>
 *   <li>{@link #ServerConfig(int, String)} / {@link #setToken(String)}：只有显式传入
 *       非空 token 时才视为配置了鉴权凭据；传入 {@code null} 或空白字符串表示"不启用 token 校验"，
 *       此时 {@code requireToken} 会被置为 {@code false}。</li>
 * </ul>
 */
public class ServerConfig implements Serializable {
    private int port = 8080;
    private String host = "0.0.0.0";
    private String token = null; // null 表示未配置 token；鉴权开关由 requireToken 独立控制
    private boolean requireToken = true; // 默认开启鉴权（默认安全）
    private int workerThreads = 16;

    public ServerConfig() {}

    public ServerConfig(int port) {
        this.port = port;
    }

    /**
     * 指定端口与 token 构造配置。
     *
     * @param port  监听端口
     * @param token 鉴权 token；只有显式传入非空 token 时鉴权保持开启，
     *              传入 {@code null} 或空白字符串则关闭鉴权（{@code requireToken = false}）
     */
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

    /**
     * 设置鉴权 token。
     *
     * <p>语义：只有显式传入非空 token 才开启鉴权；传入 {@code null} 或空白字符串
     * 表示关闭鉴权（{@code requireToken = false}）。</p>
     *
     * @param token 鉴权 token，{@code null} 或空白表示关闭鉴权
     */
    public void setToken(String token) {
        this.token = token;
        this.requireToken = (token != null && !token.isBlank());
    }

    public boolean isRequireToken() { return requireToken; }

    /**
     * 显式开关 token 鉴权。
     *
     * <p>警告：将其设为 {@code false} 会使服务进入开放（open）模式——任何能访问到
     * 服务端口的客户端都可调用全部接口，仅应在可信内网或测试环境使用。</p>
     *
     * @param requireToken {@code true} 开启鉴权（默认），{@code false} 关闭鉴权进入开放模式
     */
    public void setRequireToken(boolean requireToken) { this.requireToken = requireToken; }

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
}
