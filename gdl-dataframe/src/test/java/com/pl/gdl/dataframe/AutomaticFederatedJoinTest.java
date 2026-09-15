package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.H2Datasource;
import com.pl.gdl.dataframe.datasource.SqliteDatasource;
import com.pl.gdl.dataframe.engine.AutomaticFederatedJoinEngine;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AutomaticFederatedJoinTest {

    @Test
    public void ordinaryJoinAutomaticallyFederatesH2AndSqlite() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:auto_join_h2;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");

        CmdDataframe left = dataframe(h2, registry.createExecutionEngine(h2),
                "SELECT 1 AS id, 'alice' AS name UNION ALL SELECT 2 AS id, 'bob' AS name").alias("person");
        CmdDataframe right = dataframe(sqlite, registry.createExecutionEngine(sqlite),
                "SELECT 1 AS id, 'risk' AS department UNION ALL SELECT 3 AS id, 'ops' AS department").alias("dept");

        CmdDataframe joined = left.join(right, "person.id = dept.id");
        assertThat(((CmdDataframeImpl) joined).getExecutionEngine()).isInstanceOf(AutomaticFederatedJoinEngine.class);

        RowDataFrame rows = joined.collect();
        assertThat(rows.rowSize()).isEqualTo(1);
        assertThat((Object) rows.getRow(0).getValue("person.name")).isEqualTo("alice");
        assertThat((Object) rows.getRow(0).getValue("dept.department")).isEqualTo("risk");

        AutomaticFederatedJoinEngine engine = (AutomaticFederatedJoinEngine) ((CmdDataframeImpl) joined).getExecutionEngine();
        assertThat(engine.getLastExchangeModes()).containsExactly(
                com.pl.gdl.dataframe.federation.RowExchange.Mode.MEMORY,
                com.pl.gdl.dataframe.federation.RowExchange.Mode.MEMORY);
    }

    @Test
    public void sameTypeDifferentJdbcUrlsAreStillDifferentPhysicalSources() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource leftDs = new H2Datasource("jdbc:h2:mem:auto_join_left;DB_CLOSE_DELAY=-1", "sa", "");
        H2Datasource rightDs = new H2Datasource("jdbc:h2:mem:auto_join_right;DB_CLOSE_DELAY=-1", "sa", "");

        CmdDataframe left = dataframe(leftDs, registry.createExecutionEngine(leftDs), "SELECT 7 AS id, 'left-db' AS source").alias("l");
        CmdDataframe right = dataframe(rightDs, registry.createExecutionEngine(rightDs), "SELECT 7 AS id, 'right-db' AS source").alias("r");
        CmdDataframe joined = left.join(right, "l.id = r.id");

        assertThat(((CmdDataframeImpl) joined).getExecutionEngine()).isInstanceOf(AutomaticFederatedJoinEngine.class);
        RowDataFrame rows = joined.collect();
        assertThat(rows.rowSize()).isEqualTo(1);
        assertThat((Object) rows.getRow(0).getValue("l.source")).isEqualTo("left-db");
        assertThat((Object) rows.getRow(0).getValue("r.source")).isEqualTo("right-db");
    }

    @Test
    public void fullJoinPreservesUnmatchedRowsOnBothSides() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:auto_full;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");

        CmdDataframe left = dataframe(h2, registry.createExecutionEngine(h2),
                "SELECT 1 AS id, 'left-one' AS value UNION ALL SELECT 2 AS id, 'left-two' AS value").alias("l");
        CmdDataframe right = dataframe(sqlite, registry.createExecutionEngine(sqlite),
                "SELECT 1 AS id, 'right-one' AS value UNION ALL SELECT 3 AS id, 'right-three' AS value").alias("r");

        RowDataFrame rows = left.fullJoin(right, "l.id = r.id").collect();
        assertThat(rows.rowSize()).isEqualTo(3);
        assertThat(rows.toListMap())
                .anyMatch(row -> ((Number) row.get("l.id")).intValue() == 2 && row.get("r.id") == null)
                .anyMatch(row -> row.get("l.id") == null && ((Number) row.get("r.id")).intValue() == 3);
    }

    @Test
    public void complexJoinPredicateFailsInsteadOfChangingSemantics() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:auto_predicate;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");

        CmdDataframe left = dataframe(h2, registry.createExecutionEngine(h2), "SELECT 1 AS id").alias("l");
        CmdDataframe right = dataframe(sqlite, registry.createExecutionEngine(sqlite), "SELECT 1 AS id").alias("r");

        assertThatThrownBy(() -> left.join(right, "l.id = r.id AND l.id > 0").collect())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("one equality condition");
    }

    @Test
    public void crossAreaOrdinaryJoinRequiresRemoteDrift() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        H2Datasource h2 = new H2Datasource("jdbc:h2:mem:auto_remote;DB_CLOSE_DELAY=-1", "sa", "");
        SqliteDatasource sqlite = new SqliteDatasource(":memory:");
        h2.setAreaCode("320500");
        sqlite.setAreaCode("320600");

        CmdDataframe left = dataframe(h2, registry.createExecutionEngine(h2), "SELECT 1 AS id").alias("l");
        CmdDataframe right = dataframe(sqlite, registry.createExecutionEngine(sqlite), "SELECT 1 AS id").alias("r");

        assertThatThrownBy(() -> left.join(right, "l.id = r.id").collect())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("REMOTE_DRIFT");
    }

    private static CmdDataframe dataframe(com.pl.gdl.dataframe.datasource.CmdDatasource datasource,
                                          ExecutionEngine engine,
                                          String sql) {
        return new CmdDataframeImpl(new QueryOperator(datasource, sql), engine);
    }
}
