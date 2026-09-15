package com.pl.gdl.dataframe.datasource;

/** Lightweight health result suitable for CLI/UI diagnostics. */
public record DatasourceHealth(boolean healthy, long latencyMs, String productName,
                               String productVersion, String message) {
    public static DatasourceHealth healthy(long latencyMs, String productName, String productVersion) {
        return new DatasourceHealth(true, latencyMs, productName, productVersion, "OK");
    }

    public static DatasourceHealth unhealthy(long latencyMs, String message) {
        return new DatasourceHealth(false, latencyMs, null, null, message);
    }
}
