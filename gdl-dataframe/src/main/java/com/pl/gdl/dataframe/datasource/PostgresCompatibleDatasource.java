package com.pl.gdl.dataframe.datasource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Generic datasource implementation for PostgreSQL-wire-compatible products.
 * Product plugins only need to provide type, JDBC subprotocol, driver class and
 * default port; product-specific behavior can still override URL generation.
 */
public class PostgresCompatibleDatasource extends CmdDatasource implements JdbcDatasource {
    private final String datasourceType;
    private final String jdbcSubprotocol;
    private final String driverClassName;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final Map<String, String> parameters;

    public PostgresCompatibleDatasource(String datasourceType,
                                        String jdbcSubprotocol,
                                        String driverClassName,
                                        String host,
                                        int port,
                                        String database,
                                        String username,
                                        String password,
                                        Map<String, String> parameters) {
        super(database);
        this.datasourceType = required(datasourceType, "datasourceType").toUpperCase();
        this.jdbcSubprotocol = required(jdbcSubprotocol, "jdbcSubprotocol");
        this.driverClassName = required(driverClassName, "driverClassName");
        this.host = required(host, "host");
        if (port <= 0) throw new IllegalArgumentException("port must be > 0");
        this.port = port;
        this.database = required(database, "database");
        this.username = username;
        this.password = password;
        this.parameters = parameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(parameters));
    }

    @Override public String getDatasourceType() { return datasourceType; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public String getDriverClassName() { return driverClassName; }

    @Override
    public String getJdbcUrl() {
        String url = "jdbc:" + jdbcSubprotocol + "://" + host + ":" + port + "/" + database;
        if (parameters.isEmpty()) return url;
        StringJoiner query = new StringJoiner("&");
        parameters.forEach((key, value) -> query.add(key + "=" + value));
        return url + "?" + query;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public Map<String, String> getParameters() { return parameters; }

    @Override
    public String toString() {
        return datasourceType + "Datasource{" + host + ":" + port + "/" + database
                + ", user='" + username + "', areaCode='" + areaCode + "'}";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
