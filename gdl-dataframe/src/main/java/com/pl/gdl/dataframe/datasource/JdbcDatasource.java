package com.pl.gdl.dataframe.datasource;

/**
 * Marker contract for SQL datasources that can be executed through JDBC.
 * Implementations deliberately expose only connection metadata; pooling and
 * lifecycle remain the responsibility of the execution engine/runtime.
 */
public interface JdbcDatasource {
    String getJdbcUrl();
    String getUsername();
    String getPassword();
    String getDriverClassName();
}
