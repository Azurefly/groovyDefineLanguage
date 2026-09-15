package com.pl.gdl.runtime.plan;

import com.pl.gdl.dataframe.datasource.DatasourceCapability;

import java.util.EnumSet;
import java.util.Set;

public enum ExecutionIntent {
    READ(EnumSet.of(DatasourceCapability.READ)),
    SQL_READ(EnumSet.of(DatasourceCapability.READ, DatasourceCapability.SQL)),
    SQL_WRITE(EnumSet.of(DatasourceCapability.WRITE, DatasourceCapability.SQL));

    private final Set<DatasourceCapability> requiredCapabilities;

    ExecutionIntent(Set<DatasourceCapability> requiredCapabilities) {
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
    }

    public Set<DatasourceCapability> requiredCapabilities() {
        return requiredCapabilities;
    }
}
