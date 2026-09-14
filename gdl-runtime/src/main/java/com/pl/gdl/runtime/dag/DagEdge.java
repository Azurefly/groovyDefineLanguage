package com.pl.gdl.runtime.dag;

import java.io.Serializable;
import java.util.Objects;

public class DagEdge implements Serializable {
    private String sourceNode;
    private String targetNode;
    private String type = "endpoint";
    private String shapeType = "AdvancedBezier";
    private boolean arrow = true;

    public DagEdge() {}

    public DagEdge(String sourceNode, String targetNode) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    public String getSourceNode() { return sourceNode; }
    public void setSourceNode(String sourceNode) { this.sourceNode = sourceNode; }

    public String getTargetNode() { return targetNode; }
    public void setTargetNode(String targetNode) { this.targetNode = targetNode; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getShapeType() { return shapeType; }
    public void setShapeType(String shapeType) { this.shapeType = shapeType; }

    public boolean isArrow() { return arrow; }
    public void setArrow(boolean arrow) { this.arrow = arrow; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DagEdge dagEdge = (DagEdge) o;
        return Objects.equals(sourceNode, dagEdge.sourceNode) && Objects.equals(targetNode, dagEdge.targetNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, targetNode);
    }

    @Override
    public String toString() {
        return sourceNode + " -> " + targetNode;
    }
}
