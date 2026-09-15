package com.pl.gdl.runtime.federation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FederatedFragment implements Serializable {
    private final String id;
    private final ExecutionDomain domain;
    private final List<String> nodeIds = new ArrayList<>();

    public FederatedFragment(String id, ExecutionDomain domain) {
        this.id = id;
        this.domain = domain;
    }

    public String getId() { return id; }
    public ExecutionDomain getDomain() { return domain; }
    public List<String> getNodeIds() { return Collections.unmodifiableList(nodeIds); }
    public void addNode(String nodeId) { if (nodeId != null) nodeIds.add(nodeId); }
}
