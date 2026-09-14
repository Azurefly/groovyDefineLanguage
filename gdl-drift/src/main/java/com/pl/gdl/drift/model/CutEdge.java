package com.pl.gdl.drift.model;

import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagNode;

import java.io.Serializable;

public class CutEdge implements Serializable {
    private final DagEdge originalEdge;
    private final DagNode sourceNode;
    private final DagNode targetNode;
    private final String sourceAreaCode;
    private final String targetAreaCode;
    private final String intermediateTableName;
    private final String exchangeType;

    public CutEdge(DagEdge originalEdge, DagNode sourceNode, DagNode targetNode,
                   String sourceAreaCode, String targetAreaCode,
                   String intermediateTableName, String exchangeType) {
        this.originalEdge = originalEdge;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.sourceAreaCode = sourceAreaCode;
        this.targetAreaCode = targetAreaCode;
        this.intermediateTableName = intermediateTableName;
        this.exchangeType = exchangeType != null ? exchangeType : "frc";
    }

    public DagEdge getOriginalEdge() { return originalEdge; }
    public DagNode getSourceNode() { return sourceNode; }
    public DagNode getTargetNode() { return targetNode; }
    public String getSourceAreaCode() { return sourceAreaCode; }
    public String getTargetAreaCode() { return targetAreaCode; }
    public String getIntermediateTableName() { return intermediateTableName; }
    public String getExchangeType() { return exchangeType; }

    @Override
    public String toString() {
        return "CutEdge{" + sourceNode.getId() + "[" + sourceAreaCode + "] -> " +
                targetNode.getId() + "[" + targetAreaCode + "] via " + intermediateTableName + " (" + exchangeType + ")}";
    }
}
