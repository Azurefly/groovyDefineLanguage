package com.pl.gdl.runtime.federation;

import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits a logical DAG into contiguous execution fragments. Nodes stay in the
 * same fragment only when they are connected and share the same physical
 * execution domain (area + datasource type + datasource name).
 */
public class FederatedDagPlanner {

    public FederatedDagPlan plan(DagGraph graph) {
        Objects.requireNonNull(graph, "graph must not be null");
        FederatedDagPlan plan = new FederatedDagPlan();
        List<DagNode> ordered = graph.topologicalSort();
        if (ordered.isEmpty()) return plan;

        Map<String, String> parent = new LinkedHashMap<>();
        Map<String, ExecutionDomain> domains = new LinkedHashMap<>();
        for (DagNode node : ordered) {
            parent.put(node.getId(), node.getId());
            domains.put(node.getId(), ExecutionDomain.fromNode(node));
        }

        for (DagEdge edge : graph.getEdges()) {
            ExecutionDomain source = domains.get(edge.getSourceNode());
            ExecutionDomain target = domains.get(edge.getTargetNode());
            if (source != null && source.equals(target)) {
                union(parent, edge.getSourceNode(), edge.getTargetNode());
            }
        }

        Map<String, FederatedFragment> fragmentsByRoot = new LinkedHashMap<>();
        Map<String, String> fragmentByNode = new LinkedHashMap<>();
        int fragmentSequence = 1;
        for (DagNode node : ordered) {
            String root = find(parent, node.getId());
            FederatedFragment fragment = fragmentsByRoot.get(root);
            if (fragment == null) {
                fragment = new FederatedFragment("fragment_" + fragmentSequence++, domains.get(node.getId()));
                fragmentsByRoot.put(root, fragment);
                plan.addFragment(fragment);
            }
            fragment.addNode(node.getId());
            fragmentByNode.put(node.getId(), fragment.getId());
        }

        int exchangeSequence = 1;
        for (DagEdge edge : graph.getEdges()) {
            String sourceFragment = fragmentByNode.get(edge.getSourceNode());
            String targetFragment = fragmentByNode.get(edge.getTargetNode());
            if (sourceFragment == null || targetFragment == null || sourceFragment.equals(targetFragment)) continue;

            ExecutionDomain sourceDomain = domains.get(edge.getSourceNode());
            ExecutionDomain targetDomain = domains.get(edge.getTargetNode());
            ExchangeBoundary.MaterializationMode mode = sourceDomain.areaCode().equals(targetDomain.areaCode())
                    ? ExchangeBoundary.MaterializationMode.MEMORY
                    : ExchangeBoundary.MaterializationMode.REMOTE_DRIFT;
            plan.addExchange(new ExchangeBoundary(
                    "exchange_" + exchangeSequence++,
                    edge.getSourceNode(), edge.getTargetNode(),
                    sourceFragment, targetFragment,
                    sourceDomain, targetDomain, mode));
        }
        return plan;
    }

    private static String find(Map<String, String> parent, String node) {
        String p = parent.get(node);
        if (p == null || p.equals(node)) return node;
        String root = find(parent, p);
        parent.put(node, root);
        return root;
    }

    private static void union(Map<String, String> parent, String left, String right) {
        String leftRoot = find(parent, left);
        String rightRoot = find(parent, right);
        if (!leftRoot.equals(rightRoot)) parent.put(rightRoot, leftRoot);
    }
}
