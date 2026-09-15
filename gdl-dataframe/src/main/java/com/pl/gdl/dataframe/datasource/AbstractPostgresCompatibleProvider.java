package com.pl.gdl.dataframe.datasource;

import com.pl.gdl.dataframe.dialect.PostgresSqlDialect;
import com.pl.gdl.dataframe.dialect.SqlDialect;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * SDK base for GaussDB/KingbaseES/Vastbase and other PostgreSQL-compatible
 * plugins. Driver artifacts remain plugin-owned so the core module does not
 * force vendor JDBC dependencies.
 */
public abstract class AbstractPostgresCompatibleProvider implements DatasourceProvider {

    protected abstract String jdbcSubprotocol();
    protected abstract String driverClassName();
    protected abstract int defaultPort();

    @Override
    public Set<DatasourceCapability> getCapabilities() {
        return EnumSet.of(
                DatasourceCapability.READ,
                DatasourceCapability.WRITE,
                DatasourceCapability.SQL,
                DatasourceCapability.JDBC,
                DatasourceCapability.TRANSACTION,
                DatasourceCapability.METADATA,
                DatasourceCapability.HEALTH_CHECK);
    }

    @Override
    public SqlDialect getDialect() {
        return new PostgresSqlDialect();
    }

    @Override
    public void validateConfig(Map<String, Object> config) {
        required(config, "host");
        required(config, "database");
        Object port = config.get("port");
        if (port != null && integer(port) <= 0) throw new IllegalArgumentException("port must be > 0");
    }

    @Override
    public CmdDatasource create(Map<String, Object> config) {
        return create(config, SecretResolver.system());
    }

    @Override
    public CmdDatasource create(Map<String, Object> config, SecretResolver secretResolver) {
        String password = string(config.get("password"));
        Object passwordRef = config.get("passwordRef");
        if (passwordRef != null && !String.valueOf(passwordRef).isBlank()) {
            password = secretResolver.resolve(String.valueOf(passwordRef));
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        Object rawParameters = config.get("parameters");
        if (rawParameters instanceof Map<?, ?> map) {
            map.forEach((key, value) -> parameters.put(String.valueOf(key), String.valueOf(value)));
        }

        return new PostgresCompatibleDatasource(
                getType(), jdbcSubprotocol(), driverClassName(),
                String.valueOf(config.get("host")),
                config.get("port") == null ? defaultPort() : integer(config.get("port")),
                String.valueOf(config.get("database")),
                string(config.get("username")), password, parameters);
    }

    private static void required(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new DatasourceConfigException("Missing required configuration '" + key + "'");
        }
    }

    private static int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
