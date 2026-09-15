package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.dialect.H2SqlDialect;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DatasourceRegistryTest {

    @Test
    public void defaultRegistryExposesBuiltInDatasourceTypes() {
        assertThat(DatasourceRegistry.getDefault().getTypes())
                .contains("HIVE", "LLM", "POSTGRES", "MYSQL", "SQLITE", "H2");
        assertThat(DatasourceRegistry.getDefault().capabilities("sqlite"))
                .contains(DatasourceCapability.READ, DatasourceCapability.WRITE,
                        DatasourceCapability.SQL, DatasourceCapability.JDBC);
    }

    @Test
    public void buildsMysqlAndSqliteConfigurations() {
        MysqlDatasource mysql = (MysqlDatasource) DatasourceRegistry.getDefault().create("mysql", Map.of(
                "host", "db.internal", "port", 3307, "database", "demo", "username", "u", "password", "p"));
        assertThat(mysql.getJdbcUrl()).startsWith("jdbc:mysql://db.internal:3307/demo");

        SqliteDatasource sqlite = (SqliteDatasource) DatasourceRegistry.getDefault().create("sqlite", Map.of("path", "data/app.db"));
        assertThat(sqlite.getJdbcUrl()).isEqualTo("jdbc:sqlite:data/app.db");
    }

    @Test
    public void resolvesPasswordReferenceWithoutPlaintextCredential() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        try {
            registry.setSecretResolver(SecretResolver.fixed(Map.of("vault:demo/mysql", "resolved-password")));
            MysqlDatasource mysql = (MysqlDatasource) registry.create("mysql", Map.of(
                    "host", "db.internal", "database", "demo", "username", "u",
                    "passwordRef", "vault:demo/mysql"));
            assertThat(mysql.getPassword()).isEqualTo("resolved-password");
        } finally {
            registry.setSecretResolver(SecretResolver.system());
        }
    }

    @Test
    public void customProviderCanBeRegisteredWithoutChangingRegistry() {
        DatasourceRegistry registry = new DatasourceRegistry();
        registry.register(new DatasourceProvider() {
            @Override public String getType() { return "CUSTOM"; }
            @Override public CmdDatasource create(Map<String, Object> config) {
                return new H2Datasource("jdbc:h2:mem:custom;DB_CLOSE_DELAY=-1", "sa", "");
            }
            @Override public java.util.Set<DatasourceCapability> getCapabilities() {
                return EnumSet.of(DatasourceCapability.READ, DatasourceCapability.SQL, DatasourceCapability.JDBC);
            }
            @Override public com.pl.gdl.dataframe.dialect.SqlDialect getDialect() { return new H2SqlDialect(); }
        });

        assertThat(registry.getTypes()).contains("CUSTOM");
        assertThat(registry.create("custom", Map.of())).isInstanceOf(H2Datasource.class);
    }

    @Test
    public void capabilityLessProviderIsSafe() {
        DatasourceRegistry registry = new DatasourceRegistry();
        registry.register(new DatasourceProvider() {
            @Override public String getType() { return "EMPTY_CAPS"; }
            @Override public CmdDatasource create(Map<String, Object> config) { return new HiveDatasource(); }
        });
        assertThat(registry.capabilities("EMPTY_CAPS")).isEmpty();
    }

    @Test
    public void pooledConnectionManagerReusesDatasourcePool() throws Exception {
        H2Datasource datasource = new H2Datasource("jdbc:h2:mem:pooltest;DB_CLOSE_DELAY=-1", "sa", "");
        try (JdbcConnectionManager manager = new JdbcConnectionManager()) {
            try (Connection first = manager.getConnection(datasource)) {
                assertThat(first.isValid(1)).isTrue();
            }
            try (Connection second = manager.getConnection(datasource)) {
                assertThat(second.isValid(1)).isTrue();
            }
            assertThat(manager.poolCount()).isEqualTo(1);
        }
    }

    @Test
    public void h2ProviderExecutesRealJdbcQuery() {
        H2Datasource datasource = new H2Datasource("jdbc:h2:mem:querytest;DB_CLOSE_DELAY=-1", "sa", "");
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(datasource);
        RowDataFrame result = engine.execute(new QueryOperator(datasource, "SELECT 7 AS id, 'gdl' AS name"));

        assertThat(result.rowSize()).isEqualTo(1);
        assertThat((Integer) result.getRow(0).getValue("id")).isEqualTo(7);
        assertThat((String) result.getRow(0).getValue("name")).isEqualTo("gdl");
    }

    @Test
    public void healthAndMetadataDiscoveryUseRealJdbcMetadata() {
        H2Datasource datasource = new H2Datasource("jdbc:h2:mem:metatest;DB_CLOSE_DELAY=-1", "sa", "");
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(datasource);
        engine.execute(new QueryOperator(datasource,
                "CREATE TABLE metadata_sample(id INT PRIMARY KEY, note VARCHAR(50))"));

        DatasourceHealth health = DatasourceRegistry.getDefault().health(datasource);
        assertThat(health.healthy()).isTrue();
        assertThat(health.productName()).containsIgnoringCase("H2");

        JdbcMetadataService.Snapshot snapshot = DatasourceRegistry.getDefault().inspect(datasource);
        JdbcMetadataService.TableInfo table = snapshot.tables().stream()
                .filter(item -> item.name().equalsIgnoreCase("metadata_sample"))
                .findFirst().orElseThrow();
        assertThat(table.columns()).extracting(JdbcMetadataService.ColumnInfo::name)
                .anyMatch(name -> name.equalsIgnoreCase("id"))
                .anyMatch(name -> name.equalsIgnoreCase("note"));
    }

    @Test
    public void jdbcEngineExecutesMultipleStatementsAndPreservesQuotedSemicolons() {
        H2Datasource datasource = new H2Datasource("jdbc:h2:mem:multistmt;DB_CLOSE_DELAY=-1", "sa", "");
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(datasource);
        RowDataFrame result = engine.execute(new QueryOperator(datasource,
                "CREATE TABLE sample(id INT PRIMARY KEY, note VARCHAR(50)); " +
                "INSERT INTO sample VALUES (1, 'left;right'); " +
                "SELECT id, note FROM sample"));

        assertThat(result.rowSize()).isEqualTo(1);
        assertThat((String) result.getRow(0).getValue("note")).isEqualTo("left;right");
    }

    @Test
    public void jdbcEngineRollsBackEarlierStatementsWhenLaterStatementFails() {
        H2Datasource datasource = new H2Datasource("jdbc:h2:mem:rollbacktest;DB_CLOSE_DELAY=-1", "sa", "");
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(datasource);
        engine.execute(new QueryOperator(datasource,
                "CREATE TABLE rollback_sample(id INT PRIMARY KEY, note VARCHAR(50)); " +
                "INSERT INTO rollback_sample VALUES (1, 'base')"));

        assertThatThrownBy(() -> engine.execute(new QueryOperator(datasource,
                "INSERT INTO rollback_sample VALUES (2, 'temporary'); " +
                "INSERT INTO rollback_sample VALUES (1, 'duplicate')")))
                .isInstanceOf(RuntimeException.class);

        RowDataFrame result = engine.execute(new QueryOperator(datasource,
                "SELECT COUNT(*) AS total FROM rollback_sample"));
        assertThat(((Number) result.getRow(0).getValue("total")).intValue()).isEqualTo(1);
    }

    @Test
    public void sqliteProviderExecutesRealJdbcQuery() {
        SqliteDatasource datasource = new SqliteDatasource(":memory:");
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(datasource);
        RowDataFrame result = engine.execute(new QueryOperator(datasource, "SELECT 9 AS id, 'sqlite' AS source_name"));

        assertThat(result.rowSize()).isEqualTo(1);
        assertThat(((Number) result.getRow(0).getValue("id")).intValue()).isEqualTo(9);
        assertThat((String) result.getRow(0).getValue("source_name")).isEqualTo("sqlite");
    }

    @Test
    public void nonJdbcHealthReportsUnsupportedInsteadOfPretendingHealthy() {
        DatasourceHealth health = DatasourceRegistry.getDefault().health(new HiveDatasource());
        assertThat(health.healthy()).isFalse();
        assertThat(health.message()).contains("not implemented");
    }

    @Test
    public void unknownDatasourceTypeFailsWithAvailableTypes() {
        assertThatThrownBy(() -> DatasourceRegistry.getDefault().create("missing", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Available");
    }
}
