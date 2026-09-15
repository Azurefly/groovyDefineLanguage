package com.pl.gdl.dataframe;

import com.pl.gdl.dataframe.datasource.AbstractPostgresCompatibleProvider;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.PostgresCompatibleDatasource;
import com.pl.gdl.dataframe.datasource.SecretResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PostgresCompatibleProviderTest {

    @Test
    public void templateBuildsVendorDatasourceAndUsesRegistrySecretResolver() {
        DatasourceRegistry registry = new DatasourceRegistry();
        registry.register(new DemoGaussProvider());
        registry.setSecretResolver(SecretResolver.fixed(Map.of("vault:gauss/app", "secret-value")));

        PostgresCompatibleDatasource datasource = (PostgresCompatibleDatasource) registry.create("GAUSSDB_TEMPLATE", Map.of(
                "host", "gauss.internal",
                "database", "app",
                "username", "gdl",
                "passwordRef", "vault:gauss/app",
                "parameters", Map.of("ssl", "true")));

        assertThat(datasource.getJdbcUrl()).isEqualTo("jdbc:gaussdb://gauss.internal:8000/app?ssl=true");
        assertThat(datasource.getDriverClassName()).isEqualTo("com.huawei.gauss200.jdbc.Driver");
        assertThat(datasource.getPassword()).isEqualTo("secret-value");
        assertThat(registry.describe("GAUSSDB_TEMPLATE").dialectName()).isEqualTo("POSTGRES");
    }

    @Test
    public void templateRejectsIncompleteVendorConfiguration() {
        DatasourceRegistry registry = new DatasourceRegistry().register(new DemoGaussProvider());
        assertThatThrownBy(() -> registry.create("GAUSSDB_TEMPLATE", Map.of("host", "gauss.internal")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database");
    }

    private static final class DemoGaussProvider extends AbstractPostgresCompatibleProvider {
        @Override public String getType() { return "GAUSSDB_TEMPLATE"; }
        @Override protected String jdbcSubprotocol() { return "gaussdb"; }
        @Override protected String driverClassName() { return "com.huawei.gauss200.jdbc.Driver"; }
        @Override protected int defaultPort() { return 8000; }
    }
}
