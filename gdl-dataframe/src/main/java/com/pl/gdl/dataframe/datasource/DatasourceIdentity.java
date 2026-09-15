package com.pl.gdl.dataframe.datasource;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Password-free physical datasource identity used by federation decisions.
 * JDBC instances are distinguished by JDBC URL, while non-JDBC providers use
 * their configured datasource name.
 */
public record DatasourceIdentity(
        String areaCode,
        String datasourceType,
        String instanceKey
) implements Serializable {

    public DatasourceIdentity {
        areaCode = normalize(areaCode, "local");
        datasourceType = normalize(datasourceType, "unknown");
        instanceKey = instanceKey == null || instanceKey.isBlank() ? "default" : instanceKey.trim();
    }

    public static DatasourceIdentity from(CmdDatasource datasource) {
        Objects.requireNonNull(datasource, "datasource must not be null");
        String instance = datasource.getDsConfName();
        if (datasource instanceof JdbcDatasource jdbcDatasource) {
            instance = jdbcDatasource.getJdbcUrl();
        }
        return new DatasourceIdentity(datasource.getAreaCode(), datasource.getDatasourceType(), instance);
    }

    public boolean sameArea(DatasourceIdentity other) {
        return other != null && areaCode.equals(other.areaCode);
    }

    private static String normalize(String value, String fallback) {
        String effective = value == null || value.isBlank() ? fallback : value.trim();
        return effective.toUpperCase(Locale.ROOT);
    }
}
