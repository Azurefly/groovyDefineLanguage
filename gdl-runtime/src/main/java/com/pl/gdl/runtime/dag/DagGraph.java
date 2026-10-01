package com.pl.gdl.runtime.dag;

import com.pl.gdl.common.exception.GdlCompilationException;

import java.io.Serializable;
import java.util.*;

/**
 * GDL 执行计划的有向无环图（DAG）：由 {@link DagNode} 节点与 {@link DagEdge} 边组成，
 * 描述算子之间的数据依赖关系。
 *
 * <p>fail-fast 语义：</p>
 * <ul>
 *   <li>{@link #addEdge(String, String)} 引用了尚未 {@link #addNode(DagNode)} 的节点
 *       （悬空边）时直接抛 {@link IllegalArgumentException}，避免静默产生不可达的边；</li>
 *   <li>{@link #topologicalSort()} 检测到环时抛 {@link GdlCompilationException}，
 *       不再静默回退为无序列表。</li>
 * </ul>
 */
public class DagGraph implements Serializable {
    private final Map<String, DagNode> nodes = new LinkedHashMap<>();
    private final List<DagEdge> edges = new ArrayList<>();

    public void addNode(DagNode node) {
        if (node != null && node.getId() != null) {
            nodes.put(node.getId(), node);
        }
    }

    /**
     * 添加一条有向边。
     *
     * @param sourceNodeId 上游节点 id，必须已通过 {@link #addNode(DagNode)} 加入
     * @param targetNodeId 下游节点 id，必须已通过 {@link #addNode(DagNode)} 加入
     * @throws IllegalArgumentException 任一端点引用了图中不存在的节点（悬空边）时抛出
     */
    public void addEdge(String sourceNodeId, String targetNodeId) {
        if (sourceNodeId == null || targetNodeId == null) {
            return;
        }
        if (!nodes.containsKey(sourceNodeId)) {
            throw new IllegalArgumentException("悬空边：源节点 [" + sourceNodeId + "] 尚未通过 addNode 加入图中");
        }
        if (!nodes.containsKey(targetNodeId)) {
            throw new IllegalArgumentException("悬空边：目标节点 [" + targetNodeId + "] 尚未通过 addNode 加入图中");
        }
        DagEdge edge = new DagEdge(sourceNodeId, targetNodeId);
        if (!edges.contains(edge)) {
            edges.add(edge);
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

    /**
     * Kahn 算法拓扑排序。
     *
     * @return 按依赖顺序排列的节点
     * @throws GdlCompilationException 图中存在环、无法拓扑排序时抛出
     */
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
            throw new GdlCompilationException("DAG 存在环，无法完成拓扑排序");
        }
        return sorted;
    }
}
