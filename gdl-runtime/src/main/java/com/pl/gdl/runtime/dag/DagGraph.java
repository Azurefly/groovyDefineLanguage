package com.pl.gdl.runtime.dag;

import java.io.Serializable;
import java.util.*;

public class DagGraph implements Serializable {
    private final Map<String, DagNode> nodes = new LinkedHashMap<>();
    private final List<DagEdge> edges = new ArrayList<>();

    public void addNode(DagNode node) {
        if (node != null && node.getId() != null) {
            nodes.put(node.getId(), node);
        }
    }

    public void addEdge(String sourceNodeId, String targetNodeId) {
        if (sourceNodeId != null && targetNodeId != null) {
            DagEdge edge = new DagEdge(sourceNodeId, targetNodeId);
            if (!edges.contains(edge)) {
                edges.add(edge);
            }
        }
    }

    public DagNode getNode(String id) {
        return nodes.get(id);
    }

    public Collection<DagNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<DagEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public List<DagNode> topologicalSort() {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (String id : nodes.keySet()) {
            inDegree.put(id, 0);
            adj.put(id, new ArrayList<>());
        }

        for (DagEdge edge : edges) {
            adj.get(edge.getSourceNode()).add(edge.getTargetNode());
            inDegree.put(edge.getTargetNode(), inDegree.getOrDefault(edge.getTargetNode(), 0) + 1);
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<DagNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            sorted.add(nodes.get(u));
            for (String v : adj.get(u)) {
                inDegree.put(v, inDegree.get(v) - 1);
                if (inDegree.get(v) == 0) {
                    queue.offer(v);
                }
            }
        }

        if (sorted.size() != nodes.size()) {
            // Cycle fallback
            return new ArrayList<>(nodes.values());
        }
        return sorted;
    }
}
