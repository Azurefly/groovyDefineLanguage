package com.pl.gdl.runtime;

import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.runtime.plan.DatasourceExecutionPlanner;
import com.pl.gdl.runtime.plan.ExecutionIntent;
import com.pl.gdl.runtime.plan.ExecutionPlan;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DatasourceExecutionPlannerTest {

    @Test
    public void jdbcProviderUsesProviderPushdown() {
        DatasourceExecutionPlanner planner = new DatasourceExecutionPlanner();
        ExecutionPlan plan = planner.plan(
                new H2Datasource("jdbc:h2:mem:planner;DB_CLOSE_DELAY=-1", "sa", ""),
                ExecutionIntent.SQL_READ,
                DatasourceRegistry.getDefault(),
                new InMemoryEngine());

        assertThat(plan.mode()).isEqualTo(ExecutionPlan.Mode.PROVIDER_PUSHDOWN);
        assertThat(plan.datasourceType()).isEqualTo("H2");
        assertThat(plan.reason()).contains("Provider supplies");
    }

    @Test
    public void hiveCapabilitiesCanUseExistingFallbackExecutionPath() {
        DatasourceExecutionPlanner planner = new DatasourceExecutionPlanner();
        InMemoryEngine fallback = new InMemoryEngine();
        ExecutionPlan plan = planner.plan(
                new HiveDatasource(),
                ExecutionIntent.SQL_READ,
                DatasourceRegistry.getDefault(),
                fallback);

        assertThat(plan.mode()).isEqualTo(ExecutionPlan.Mode.FALLBACK);
        assertThat(plan.engine()).isSameAs(fallback);
        assertThat(plan.datasourceType()).isEqualTo("HIVE");
    }

    @Test
    public void plannerRejectsWriteWhenProviderDoesNotDeclareWriteCapability() {
        DatasourceRegistry registry = new DatasourceRegistry();
        registry.register(new DatasourceProvider() {
            @Override public String getType() { return "READ_ONLY"; }
            @Override public CmdDatasource create(Map<String, Object> config) {
                return new CmdDatasource() {
                    @Override public String getDatasourceType() { return "READ_ONLY"; }
                };
            }
            @Override public java.util.Set<DatasourceCapability> getCapabilities() {
                return EnumSet.of(DatasourceCapability.READ, DatasourceCapability.SQL);
            }
        });

        CmdDatasource datasource = registry.create("READ_ONLY", Map.of());
        assertThatThrownBy(() -> new DatasourceExecutionPlanner().plan(
                datasource,
                ExecutionIntent.SQL_WRITE,
                registry,
                new InMemoryEngine()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WRITE")
                .hasMessageContaining("READ_ONLY");
    }
}
