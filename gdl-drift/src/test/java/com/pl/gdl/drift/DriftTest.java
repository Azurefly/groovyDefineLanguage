package com.pl.gdl.drift;

import com.pl.gdl.drift.model.DriftPlan;
import com.pl.gdl.drift.orchestrator.FederatedExecutionCoordinator;
import com.pl.gdl.drift.transport.IntermediateTableManager;
import com.pl.gdl.runtime.dag.DagEdge;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;
import com.pl.gdl.drift.algorithm.SubgraphPartitioner;
import com.pl.gdl.drift.codegen.SubgraphScriptGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DriftTest {

    @Test
    public void testSubgraphBoundaryCutting() {
        DagGraph dag = new DagGraph();

        DagNode n1 = new DagNode("node_1", "from remote", "FromOperator", "table");
        n1.setAreaCode("320100");

        DagNode n2 = new DagNode("node_2", "select", "SelectOperator", "select");
        n2.setAreaCode("320100");

        DagNode n3 = new DagNode("node_3", "to local", "ToOperator", "table");
        n3.setAreaCode("320000");

        dag.addNode(n1);
        dag.addNode(n2);
        dag.addNode(n3);

        dag.addEdge("node_1", "node_2");
        dag.addEdge("node_2", "node_3");

        SubgraphPartitioner partitioner = new SubgraphPartitioner();
        DriftPlan plan = partitioner.partition("test_script", dag, "320000");

        assertThat(plan.hasDrift()).isTrue();
        assertThat(plan.getAllCuts()).hasSize(1);
        assertThat(plan.getAllCuts().get(0).getSourceAreaCode()).isEqualTo("320100");
        assertThat(plan.getAllCuts().get(0).getTargetAreaCode()).isEqualTo("320000");
        assertThat(plan.getAllCuts().get(0).getIntermediateTableName()).startsWith("tre_temp_");

        SubgraphScriptGenerator generator = new SubgraphScriptGenerator();
        generator.generateScripts(plan);

        assertThat(plan.getRemoteSubgraphs().get("320100").getGeneratedScript()).contains("driftTo(\"320000\")");
        assertThat(plan.getLocalSubgraph().getGeneratedScript()).contains("driftFrom");
    }

    @Test
    public void testIntermediateTableLifecycle() {
        IntermediateTableManager mgr = IntermediateTableManager.getInstance();
        String tempTable = mgr.generateTempTableName();
        assertThat(tempTable).startsWith("tre_temp_");
        assertThat(mgr.getActiveTempTables()).contains(tempTable);

        mgr.release(tempTable);
        assertThat(mgr.getActiveTempTables()).doesNotContain(tempTable);
    }
}
