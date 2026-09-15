package com.pl.gdl.runtime;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.H2Datasource;
import com.pl.gdl.dataframe.datasource.SqliteDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagNode;
import com.pl.gdl.runtime.federation.ExchangeBoundary;
import com.pl.gdl.runtime.federation.FederatedDagPlan;
import com.pl.gdl.runtime.federation.FederatedDagPlanner;
import com.pl.gdl.runtime.federation.FederatedJoinExecutor;
import com.pl.gdl.runtime.federation.FederatedJoinRequest;
import com.pl.gdl.runtime.federation.FederatedJoinResult;
import com.pl.gdl.runtime.plan.DatasourceExecutionPlanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FederatedExecutionTest {

    @Test
    public void dagPlannerSplitsByDatasourceAndAreaBoundaries() {
        DagGraph graph = new DagGraph();
        graph.addNode(node("n1", "local", "H2", "primary"));
        graph.addNode(node("n2", "local", "H2", "primary"));
        graph.addNode(node("n3", "local", "SQLITE", "cache"));
        graph.addNode(node("n4", "320500", "SQLITE", "cache"));
        graph.addEdge("n1", "n2");
        graph.addEdge("n2", "n3");
        graph.addEdge("n3", "n4");

        FederatedDagPlan plan = new FederatedDagPlanner().plan(graph);

        assertThat(plan.isFederated()).isTrue();
        assertThat(plan.getFragments()).hasSize(3);
        assertThat(plan.getFragments().get(0).getNodeIds()).containsExactly("n1", "n2");
        assertThat(plan.getExchanges()).hasSize(2);
        assertThat(plan.getExchanges())
                .extracting(ExchangeBoundary::mode)
                .containsExactly(ExchangeBoundary.MaterializationMode.MEMORY,
                        ExchangeBoundary.MaterializationMode.REMOTE_DRIFT);
    }

    @Test
    public void realH2AndSqliteQueriesJoinThroughMemoryExchange() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:federated_h2;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");

        registry.createExecutionEngine(h2).execute(new QueryOperator(h2,
                "CREATE TABLE person(id INT PRIMARY KEY, name VARCHAR(50)); " +
                        "INSERT INTO person VALUES (1, 'alice'), (2, 'bob')"));
        registry.createExecutionEngine(sqlite).execute(new QueryOperator(sqlite,
                "CREATE TABLE dept(id INTEGER PRIMARY KEY, department TEXT); " +
                        "INSERT INTO dept VALUES (1, 'risk'), (3, 'ops')"));

        FederatedJoinExecutor executor = new FederatedJoinExecutor(
                registry, new DatasourceExecutionPlanner(), new InMemoryEngine());
        FederatedJoinResult result = executor.execute(new FederatedJoinRequest(
                h2, "SELECT id, name FROM person", "id", "person",
                sqlite, "SELECT id, department FROM dept", "id", "dept",
                FederatedJoinRequest.JoinType.INNER));

        assertThat(result.rows().rowSize()).isEqualTo(1);
        assertThat(((Number) result.rows().getRow(0).getValue("person.id")).intValue()).isEqualTo(1);
        assertThat(result.rows().getRow(0).getValue("person.name")).isEqualTo("alice");
        assertThat(result.rows().getRow(0).getValue("dept.department")).isEqualTo("risk");
        assertThat(result.sourcePlans()).hasSize(2);
        assertThat(result.exchanges()).hasSize(2);
        assertThat(result.exchanges()).allMatch(exchange -> exchange.mode() == ExchangeBoundary.MaterializationMode.MEMORY);
    }

    @Test
    public void leftFederatedJoinKeepsUnmatchedLeftRows() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:federated_left;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");

        FederatedJoinExecutor executor = new FederatedJoinExecutor(
                registry, new DatasourceExecutionPlanner(), new InMemoryEngine());
        FederatedJoinResult result = executor.execute(new FederatedJoinRequest(
                h2, "SELECT 1 AS id, 'alice' AS name UNION ALL SELECT 2 AS id, 'bob' AS name", "id", "p",
                sqlite, "SELECT 1 AS id, 'risk' AS department", "id", "d",
                FederatedJoinRequest.JoinType.LEFT));

        assertThat(result.rows().rowSize()).isEqualTo(2);
        assertThat(result.rows().toListMap())
                .anyMatch(row -> ((Number) row.get("p.id")).intValue() == 2 && row.get("d.department") == null);
    }

    @Test
    public void crossAreaFederationRequiresDriftInsteadOfPretendingLocal() {
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:federated_remote;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");
        h2.setAreaCode("320500");
        sqlite.setAreaCode("320600");

        FederatedJoinExecutor executor = new FederatedJoinExecutor();
        assertThatThrownBy(() -> executor.execute(new FederatedJoinRequest(
                h2, "SELECT 1 AS id", "id", "left",
                sqlite, "SELECT 1 AS id", "id", "right",
                FederatedJoinRequest.JoinType.INNER)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("REMOTE_DRIFT");
    }

    private static DagNode node(String id, String areaCode, String datasourceType, String datasourceName) {
        DagNode node = new DagNode(id, id, "QueryOperator", "query");
        node.setAreaCode(areaCode);
        node.setProperty("datasourceType", datasourceType);
        node.setProperty("datasourceName", datasourceName);
        return node;
    }
}
