package com.pl.gdl.drift.model;

import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ExecutionSubgraph implements Serializable {
    private final String areaCode;
    private final boolean local;
    private final List<DagNode> nodes = new ArrayList<>();
    private final List<DagEdge> edges = new ArrayList<>();
    private final List<CutEdge> outboundCuts = new ArrayList<>();
    private final List<CutEdge> inboundCuts = new ArrayList<>();
    private String generatedScript;

    public ExecutionSubgraph(String areaCode, boolean local) {
        this.areaCode = areaCode;
        this.local = local;
    }

    public String getAreaCode() { return areaCode; }
    public boolean isLocal() { return local; }
    public List<DagNode> getNodes() { return nodes; }
    public List<DagEdge> getEdges() { return edges; }
    public List<CutEdge> getOutboundCuts() { return outboundCuts; }
    public List<CutEdge> getInboundCuts() { return inboundCuts; }

    public String getGeneratedScript() { return generatedScript; }
    public void setGeneratedScript(String generatedScript) { this.generatedScript = generatedScript; }

    public void addNode(DagNode node) { this.nodes.add(node); }
    public void addEdge(DagEdge edge) { this.edges.add(edge); }
    public void addOutboundCut(CutEdge cut) { this.outboundCuts.add(cut); }
    public void addInboundCut(CutEdge cut) { this.inboundCuts.add(cut); }
}
