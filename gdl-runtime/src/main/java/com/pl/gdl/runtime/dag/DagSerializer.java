package com.pl.gdl.runtime.dag;

import groovy.json.JsonOutput;
import java.util.*;

public class DagSerializer {

    public static Map<String, Object> toCanvasMap(DagGraph graph) {
        Map<String, Object> canvas = new LinkedHashMap<>();
        List<Map<String, Object>> nodesList = new ArrayList<>();
        List<Map<String, Object>> edgesList = new ArrayList<>();

        int currentTop = 74;
        for (DagNode node : graph.getNodes()) {
            Map<String, Object> nodeMap = new LinkedHashMap<>();
            nodeMap.put("id", node.getId());
            nodeMap.put("label", node.getLabel());
            nodeMap.put("operator", node.getOperator());
            nodeMap.put("status", "unknown");
            nodeMap.put("type", node.getType());
            nodeMap.put("left", node.getLeft());
            nodeMap.put("top", node.getTop() > 0 ? node.getTop() : currentTop);
            currentTop += 74;
            nodesList.add(nodeMap);
        }

        for (DagEdge edge : graph.getEdges()) {
            Map<String, Object> edgeMap = new LinkedHashMap<>();
            edgeMap.put("source", "bottom");
            edgeMap.put("sourceNode", edge.getSourceNode());
            edgeMap.put("target", "top");
            edgeMap.put("targetNode", edge.getTargetNode());
            edgeMap.put("type", edge.getType());
            edgeMap.put("arrow", edge.isArrow());
            edgeMap.put("shapeType", edge.getShapeType());
            edgesList.add(edgeMap);
        }

        canvas.put("nodes", nodesList);
        canvas.put("edges", edgesList);
        canvas.put("groups", List.of());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("canvas", canvas);
        return root;
    }

    public static String toJson(DagGraph graph) {
        return JsonOutput.toJson(toCanvasMap(graph));
    }
}
