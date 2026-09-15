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

    public Connection getConnection(JdbcDatasource datasource) throws SQLException {
        Objects.requireNonNull(datasource, "datasource must not be null");
        loadDriver(datasource);
        return pools.computeIfAbsent(PoolKey.from(datasource), key -> createPool(datasource)).getConnection();
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
        if ("jdbc:sqlite::memory:".equalsIgnoreCase(jdbcUrl)) return 1;
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
