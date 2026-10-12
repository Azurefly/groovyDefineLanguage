package com.pl.gdl.common.util;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC 语句配置工具。
 *
 * <p>统一应用查询相关的 Statement 配置，避免驱动默认行为导致大结果集问题：
 * <ul>
 *   <li>{@code gdl.jdbc.fetchSize}：JDBC fetchSize，默认 1000。设为 0 表示使用驱动默认。
 *       MySQL 等驱动默认会将整个结果集加载到内存，设置 fetchSize 可启用流式读取，
 *       显著降低大结果集的内存占用。</li>
 * </ul>
 * </p>
 */
public final class JdbcUtils {

    private JdbcUtils() {}

    /**
     * 获取配置的 fetchSize。
     *
     * @return fetchSize，默认 1000；0 表示不设置（驱动默认）
     */
    public static int fetchSize() {
        return Integer.getInteger("gdl.jdbc.fetchSize", 1000);
    }

    /**
     * 为已创建的 Statement 应用 fetchSize 配置。
     *
     * @param stmt 目标 Statement
     * @throws SQLException 设置失败时抛出
     */
    public static void configure(Statement stmt) throws SQLException {
        int fs = fetchSize();
        if (fs > 0) {
            stmt.setFetchSize(fs);
        }
    }

    /**
     * 创建已应用 fetchSize 配置的 Statement。
     *
     * @param conn 数据库连接
     * @return 配置好的 Statement
     * @throws SQLException 创建或配置失败时抛出
     */
    public static Statement createStatement(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        configure(stmt);
        return stmt;
    }
}
