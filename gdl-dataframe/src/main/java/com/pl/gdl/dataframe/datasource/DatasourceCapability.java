package com.pl.gdl.dataframe.datasource;

/**
 * Capabilities exposed by a datasource provider. Consumers can inspect these
 * instead of hard-coding behavior by datasource type.
 */
public enum DatasourceCapability {
    READ,
    WRITE,
    SQL,
    JDBC,
    TRANSACTION,
    PARTITIONED_WRITE,
    REMOTE_EXECUTION,
    LLM
}
