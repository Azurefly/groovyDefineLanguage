package com.pl.gdl.dataframe.datasource;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable provider metadata exposed to tooling, planners and diagnostics. */
public record DatasourceProviderDescriptor(
        String type,
        String version,
        Set<DatasourceCapability> capabilities,
        String dialectName
) {
    public DatasourceProviderDescriptor {
        capabilities = capabilities == null || capabilities.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
    }
}
