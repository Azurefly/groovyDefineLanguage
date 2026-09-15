package com.pl.gdl.runtime.plan;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceCapability;
import com.pl.gdl.dataframe.datasource.DatasourceProvider;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.engine.ExecutionEngine;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Chooses datasource execution using declared provider capabilities instead of
 * datasource-type conditionals.
 */
public class DatasourceExecutionPlanner {
    public ExecutionPlan plan(CmdDatasource datasource,
                              ExecutionIntent intent,
                              DatasourceRegistry registry,
                              ExecutionEngine fallbackEngine) {
        Objects.requireNonNull(intent, "intent must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(fallbackEngine, "fallbackEngine must not be null");

        if (datasource == null) {
            return new ExecutionPlan(ExecutionPlan.Mode.FALLBACK, intent, null, fallbackEngine,
                    "No datasource was supplied; using the configured fallback engine");
        }

        DatasourceProvider provider = registry.require(datasource.getDatasourceType());
        Set<DatasourceCapability> actual = provider.getCapabilities();
        Set<DatasourceCapability> missing = EnumSet.noneOf(DatasourceCapability.class);
        missing.addAll(intent.requiredCapabilities());
        if (actual != null) missing.removeAll(actual);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Datasource provider '" + provider.getType()
                    + "' cannot satisfy " + intent + "; missing capabilities " + missing
                    + ", declared capabilities=" + (actual == null ? Set.of() : actual));
        }

        ExecutionEngine providerEngine = provider.createExecutionEngine(datasource);
        if (providerEngine != null) {
            return new ExecutionPlan(ExecutionPlan.Mode.PROVIDER_PUSHDOWN, intent,
                    provider.getType(), providerEngine,
                    "Provider supplies an execution engine for the required capabilities");
        }

        return new ExecutionPlan(ExecutionPlan.Mode.FALLBACK, intent,
                provider.getType(), fallbackEngine,
                "Capabilities are satisfied but provider delegates execution to the configured fallback engine");
    }
}
