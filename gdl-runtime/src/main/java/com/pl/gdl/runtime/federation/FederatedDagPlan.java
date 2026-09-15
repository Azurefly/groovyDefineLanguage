package com.pl.gdl.runtime.federation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FederatedDagPlan implements Serializable {
    private final List<FederatedFragment> fragments = new ArrayList<>();
    private final List<ExchangeBoundary> exchanges = new ArrayList<>();

    public List<FederatedFragment> getFragments() { return Collections.unmodifiableList(fragments); }
    public List<ExchangeBoundary> getExchanges() { return Collections.unmodifiableList(exchanges); }
    public void addFragment(FederatedFragment fragment) { if (fragment != null) fragments.add(fragment); }
    public void addExchange(ExchangeBoundary exchange) { if (exchange != null) exchanges.add(exchange); }
    public boolean isFederated() { return fragments.size() > 1 || !exchanges.isEmpty(); }
}
