package com.pl.gdl.drift.orchestrator;

import com.pl.gdl.drift.model.DriftAwareFederationPlan;
import com.pl.gdl.drift.transport.IntermediateTableManager;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.federation.ExchangeBoundary;
import com.pl.gdl.runtime.federation.FederatedDagPlan;
import com.pl.gdl.runtime.federation.FederatedDagPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges provider-aware DAG partitioning with the existing Drift transport
 * layer. Local cross-provider exchanges stay in memory; cross-area exchanges
 * receive an intermediate table and are explicitly marked for remote Drift.
 */
public class DriftAwareFederationPlanner {
    private final FederatedDagPlanner federatedPlanner;
    private final IntermediateTableManager intermediateTableManager;

    public DriftAwareFederationPlanner() {
        this(new FederatedDagPlanner(), IntermediateTableManager.getInstance());
    }

    public DriftAwareFederationPlanner(FederatedDagPlanner federatedPlanner,
                                       IntermediateTableManager intermediateTableManager) {
        this.federatedPlanner = Objects.requireNonNull(federatedPlanner, "federatedPlanner must not be null");
        this.intermediateTableManager = Objects.requireNonNull(intermediateTableManager, "intermediateTableManager must not be null");
    }

    public DriftAwareFederationPlan plan(DagGraph graph) {
        FederatedDagPlan federatedPlan = federatedPlanner.plan(graph);
        List<DriftAwareFederationPlan.RemoteExchange> remote = new ArrayList<>();
        for (ExchangeBoundary exchange : federatedPlan.getExchanges()) {
            if (exchange.mode() == ExchangeBoundary.MaterializationMode.REMOTE_DRIFT) {
                remote.add(new DriftAwareFederationPlan.RemoteExchange(
                        exchange,
                        intermediateTableManager.generateTempTableName()));
            }
        }
        return new DriftAwareFederationPlan(federatedPlan, List.copyOf(remote));
    }
}
