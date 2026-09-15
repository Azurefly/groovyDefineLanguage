package com.pl.gdl.dataframe.datasource;

import com.pl.gdl.dataframe.dialect.*;
import com.pl.gdl.dataframe.engine.ExecutionEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe datasource provider registry. Built-in providers are registered
 * once, while external providers can be added programmatically or discovered
 * with Java ServiceLoader without changing this class.
 */
public class DatasourceRegistry {
    private static final DatasourceRegistry DEFAULT = createDefault();
    private final Map<String, DatasourceProvider> providers = new ConcurrentHashMap<>();

    public DatasourceRegistry() {
        ServiceLoader.load(DatasourceProvider.class).forEach(this::register);
    }

    public static DatasourceRegistry getDefault() {
        return DEFAULT;
    }

    private static DatasourceRegistry createDefault() {
        DatasourceRegistry registry = new DatasourceRegistry();
        registerBuiltIns(registry);
        return registry;
    }

    public DatasourceRegistry register(DatasourceProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        String type = normalize(provider.getType());
        if (type.isEmpty()) throw new IllegalArgumentException("provider type must not be blank");
        providers.put(type, provider);
        return this;
    }

    public Optional<DatasourceProvider> find(String type) {
        return Optional.ofNullable(providers.get(normalize(type)));
    }

    public DatasourceProvider require(String type) {
        return find(type).orElseThrow(() -> new IllegalArgumentException(
                "Unsupported datasource type '" + type + "'. Available: " + getTypes()));
    }

    public CmdDatasource create(String type, Map<String, Object> config) {
        return require(type).create(config == null ? Map.of() : config);
    }

    public ExecutionEngine createExecutionEngine(CmdDatasource datasource) {
        if (datasource == null) return null;
        return require(datasource.getDatasourceType()).createExecutionEngine(datasource);
    }

    public Set<DatasourceCapability> capabilities(String type) {
        Set<DatasourceCapability> capabilities = require(type).getCapabilities();
        if (capabilities == null || capabilities.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }

    public Set<String> getTypes() {
        return Collections.unmodifiableSet(new TreeSet<>(providers.keySet()));
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static void registerBuiltIns(DatasourceRegistry registry) {
        Set<DatasourceCapability> jdbcCaps = EnumSet.of(
                DatasourceCapability.READ, DatasourceCapability.WRITE,
                DatasourceCapability.SQL, DatasourceCapability.JDBC,
                DatasourceCapability.TRANSACTION);

        registry.register(provider("POSTGRES", jdbcCaps, new PostgresSqlDialect(), cfg ->
                new PostgresDatasource(str(cfg, "host", "localhost"), integer(cfg, "port", 5432),
                        str(cfg, "database", "postgres"), str(cfg, "username", null), str(cfg, "password", null))));
        registry.register(provider("MYSQL", jdbcCaps, new MysqlSqlDialect(), cfg ->
                new MysqlDatasource(str(cfg, "host", "localhost"), integer(cfg, "port", 3306),
                        str(cfg, "database", "mysql"), str(cfg, "username", null), str(cfg, "password", null))));
        registry.register(provider("SQLITE", jdbcCaps, new SqliteSqlDialect(), cfg ->
                new SqliteDatasource(str(cfg, "path", ":memory:"))));
        registry.register(provider("H2", jdbcCaps, new H2SqlDialect(), cfg ->
                new H2Datasource(str(cfg, "url", "jdbc:h2:mem:gdl;DB_CLOSE_DELAY=-1"),
                        str(cfg, "username", "sa"), str(cfg, "password", ""))));
        registry.register(provider("HIVE", EnumSet.of(DatasourceCapability.READ, DatasourceCapability.WRITE,
                DatasourceCapability.SQL, DatasourceCapability.PARTITIONED_WRITE), new HiveSqlDialect(), cfg ->
                new HiveDatasource(str(cfg, "confName", "default"))));
        registry.register(provider("LLM", EnumSet.of(DatasourceCapability.LLM, DatasourceCapability.REMOTE_EXECUTION), null, cfg ->
                new LlmDatasource(str(cfg, "url", null), integer(cfg, "concurrent", 10))));
    }

    private static DatasourceProvider provider(String type, Set<DatasourceCapability> capabilities,
                                               SqlDialect dialect,
                                               java.util.function.Function<Map<String, Object>, CmdDatasource> factory) {
        return new DatasourceProvider() {
            @Override public String getType() { return type; }
            @Override public CmdDatasource create(Map<String, Object> config) { return factory.apply(config); }
            @Override public Set<DatasourceCapability> getCapabilities() { return capabilities; }
            @Override public SqlDialect getDialect() { return dialect; }
        };
    }

    private static String str(Map<String, Object> cfg, String key, String defaultValue) {
        Object value = cfg.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int integer(Map<String, Object> cfg, String key, int defaultValue) {
        Object value = cfg.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
}
