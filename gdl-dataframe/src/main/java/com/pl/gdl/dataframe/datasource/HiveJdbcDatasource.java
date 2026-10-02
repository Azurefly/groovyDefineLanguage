package com.pl.gdl.dataframe.datasource;

/**
 * Hive datasource backed by HiveServer2 JDBC.
 * Connects to HiveServer2 via the Hive JDBC driver.
 */
public class HiveJdbcDatasource extends CmdDatasource implements JdbcDatasource {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public HiveJdbcDatasource(String jdbcUrl) {
        this(jdbcUrl, "", "");
    }

    public HiveJdbcDatasource(String jdbcUrl, String username, String password) {
        super("default");
        this.jdbcUrl = jdbcUrl;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    public String getDatasourceType() {
        return "HIVE";
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getDriverClassName() {
        return "org.apache.hive.jdbc.HiveDriver";
    }

    @Override
    public String toString() {
        return "HiveJdbcDatasource{url='" + jdbcUrl + "', user='" + username + "'}";
    }
}
