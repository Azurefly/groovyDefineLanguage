package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.exception.GdlExecutionException;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reuses JDBC connections through HikariCP. Pools are isolated by URL/user/password/driver.
 */
public final class JdbcConnectionManager implements AutoCloseable {
    private static final JdbcConnectionManager DEFAULT = new JdbcConnectionManager();
    private final Map<PoolKey, HikariDataSource> pools = new ConcurrentHashMap<>();

    public static JdbcConnectionManager getDefault() {
        return DEFAULT;
    }

    /**
     * 获取指定数据源的 JDBC 连接（连接池复用）。
     * <p>
     * 防密码轮换僵尸池：若当前线程请求的参数（URL/用户名/驱动）与已存在的某个连接池
     * 完全相同、仅密码不同，说明密码可能已轮换；此时先关闭并移除旧池，再按新密码
     * 建池，防止旧池继续用过期密码建连接导致持续鉴权失败。
     */
    public Connection getConnection(JdbcDatasource datasource) throws SQLException {
        Objects.requireNonNull(datasource, "datasource must not be null");
        loadDriver(datasource);
        PoolKey key = PoolKey.from(datasource);
        HikariDataSource pool = pools.get(key);
        if (pool != null) {
            return pool.getConnection();
        }
        // 查找除密码外相同（URL/用户名/驱动）的旧池：密码变化时旧池已无用，先关闭
        PoolKey rotatedKey = null;
        for (PoolKey existing : pools.keySet()) {
            if (existing.url().equals(key.url())
                    && Objects.equals(existing.username(), key.username())
                    && Objects.equals(existing.driver(), key.driver())
                    && !Objects.equals(existing.password(), key.password())) {
                rotatedKey = existing;
                break;
            }
        }
        if (rotatedKey != null) {
            HikariDataSource stale = pools.remove(rotatedKey);
            if (stale != null) {
                stale.close();
            }
        }
        return pools.computeIfAbsent(key, k -> createPool(datasource)).getConnection();
    }

    public int poolCount() {
        return pools.size();
    }

    public void close(JdbcDatasource datasource) {
        HikariDataSource dataSource = pools.remove(PoolKey.from(datasource));
        if (dataSource != null) dataSource.close();
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private HikariDataSource createPool(JdbcDatasource datasource) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(datasource.getJdbcUrl());
        if (datasource.getUsername() != null) config.setUsername(datasource.getUsername());
        if (datasource.getPassword() != null) config.setPassword(datasource.getPassword());
        if (datasource.getDriverClassName() != null && !datasource.getDriverClassName().isBlank()) {
            config.setDriverClassName(datasource.getDriverClassName());
        }
        config.setPoolName("gdl-" + Integer.toHexString(PoolKey.from(datasource).hashCode()));
        config.setMinimumIdle(0);
        config.setMaximumPoolSize(maximumPoolSize(datasource.getJdbcUrl()));
        config.setConnectionTimeout(Long.getLong("gdl.jdbc.pool.connectionTimeoutMs", 5000L));
        config.setValidationTimeout(Long.getLong("gdl.jdbc.pool.validationTimeoutMs", 3000L));
        config.setIdleTimeout(Long.getLong("gdl.jdbc.pool.idleTimeoutMs", 60000L));
        return new HikariDataSource(config);
    }

    private int maximumPoolSize(String jdbcUrl) {
        // SQLite 内存库（jdbc:sqlite::memory: 或 file::memory:?cache=shared 等）所有连接必须共享
        // 同一个底层连接才能看到同一份数据，因此池大小固定为 1
        if (jdbcUrl != null && jdbcUrl.contains(":memory:")) return 1;
        return Math.max(1, Integer.getInteger("gdl.jdbc.pool.maximumPoolSize", 10));
    }

    private void loadDriver(JdbcDatasource datasource) {
        String driver = datasource.getDriverClassName();
        if (driver == null || driver.isBlank()) return;
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new GdlExecutionException("JDBC driver not found: " + driver, e);
        }
    }

    private record PoolKey(String url, String username, String password, String driver) {
        static PoolKey from(JdbcDatasource datasource) {
            return new PoolKey(datasource.getJdbcUrl(), datasource.getUsername(), datasource.getPassword(),
                    datasource.getDriverClassName());
        }
    }
}
