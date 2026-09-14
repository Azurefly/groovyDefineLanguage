package com.pl.gdl.runtime.dag;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class DagNode implements Serializable {
    private String id;
    private String label;
    private String operator;
    private String type = "unknown";
    private String areaCode = "local";
    private int left = 200;
    private int top = 100;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public DagNode() {}

    public DagNode(String id, String label, String operator, String type) {
        this.id = id;
        this.label = label;
        this.operator = operator;
        this.type = type != null ? type : "unknown";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAreaCode() { return areaCode; }
    public void setAreaCode(String areaCode) { this.areaCode = areaCode; }

    public int getLeft() { return left; }
    public void setLeft(int left) { this.left = left; }

    public int getTop() { return top; }
    public void setTop(int top) { this.top = top; }

    public Map<String, Object> getProperties() { return properties; }
    public void setProperty(String key, Object value) { properties.put(key, value); }

    @Override
    public String toString() {
        return "DagNode{" + id + ": " + label + " (" + operator + ")}";
    }
}
