package com.pl.gdl.drift.model;

import com.pl.gdl.runtime.federation.ExchangeBoundary;
import com.pl.gdl.runtime.federation.FederatedDagPlan;

import java.io.Serializable;
import java.util.List;

public record DriftAwareFederationPlan(
        FederatedDagPlan federatedPlan,
        List<RemoteExchange> remoteExchanges
) implements Serializable {
    public boolean requiresRemoteDrift() {
        return !remoteExchanges.isEmpty();
    }

    public record RemoteExchange(
            ExchangeBoundary exchange,
            String intermediateTableName
    ) implements Serializable {}
}
