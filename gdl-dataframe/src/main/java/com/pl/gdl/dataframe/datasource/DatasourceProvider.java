package com.pl.gdl.dataframe.datasource;

import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcExecutionEngine;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * SPI for datasource plugins. External modules can register implementations
 * through DatasourceRegistry#register or Java ServiceLoader.
 */
public interface DatasourceProvider {
    String getType();

    CmdDatasource create(Map<String, Object> config);

    /** Provider contract version, independent from the database product version. */
    default String getVersion() {
        return "1.0";
    }

    /** Validate configuration before datasource construction. */
    default void validateConfig(Map<String, Object> config) {
        // permissive by default for backwards-compatible external providers
    }

    default Set<DatasourceCapability> getCapabilities() {
        return Collections.emptySet();
    }

    default SqlDialect getDialect() {
        return null;
    }

    default DatasourceProviderDescriptor describe() {
        SqlDialect dialect = getDialect();
        return new DatasourceProviderDescriptor(
                getType(),
                getVersion(),
                getCapabilities(),
                dialect == null ? null : dialect.getDialectName());
    }

    default ExecutionEngine createExecutionEngine(CmdDatasource datasource) {
        if (datasource instanceof JdbcDatasource jdbcDatasource && getDialect() != null) {
            return new JdbcExecutionEngine(jdbcDatasource, getDialect());
        }
        return null;
    }
}
