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

    default Set<DatasourceCapability> getCapabilities() {
        return Collections.emptySet();
    }

    default SqlDialect getDialect() {
        return null;
    }

    default ExecutionEngine createExecutionEngine(CmdDatasource datasource) {
        if (datasource instanceof JdbcDatasource jdbcDatasource && getDialect() != null) {
            return new JdbcExecutionEngine(jdbcDatasource, getDialect());
        }
        return null;
    }
}
