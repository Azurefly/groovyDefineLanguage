package com.pl.gdl.drift.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DriftPlan implements Serializable {
    private final String originalScript;
    private final String localAreaCode;
    private ExecutionSubgraph localSubgraph;
    private final Map<String, ExecutionSubgraph> remoteSubgraphs = new LinkedHashMap<>();
    private final List<CutEdge> allCuts = new ArrayList<>();

    public DriftPlan(String originalScript, String localAreaCode) {
        this.originalScript = originalScript;
        this.localAreaCode = localAreaCode != null ? localAreaCode : "local";
    }

    public String getOriginalScript() { return originalScript; }
    public String getLocalAreaCode() { return localAreaCode; }

    public ExecutionSubgraph getLocalSubgraph() { return localSubgraph; }
    public void setLocalSubgraph(ExecutionSubgraph localSubgraph) { this.localSubgraph = localSubgraph; }

    public Map<String, ExecutionSubgraph> getRemoteSubgraphs() { return remoteSubgraphs; }
    public void addRemoteSubgraph(String areaCode, ExecutionSubgraph subgraph) {
        this.remoteSubgraphs.put(areaCode, subgraph);
    }

    public List<CutEdge> getAllCuts() { return allCuts; }
    public void addCut(CutEdge cut) { this.allCuts.add(cut); }

    public boolean hasDrift() {
        return !remoteSubgraphs.isEmpty();
    }
}
