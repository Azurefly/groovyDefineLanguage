package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.dialect.SqliteSqlDialect;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcExecutionEngine;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.DataQualityException;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 复杂项目实测回归：JdbcExecutionEngine 在真实 JDBC 数据源上的 4 个 P0 修复。
 *
 * <ul>
 *   <li>JoinOperator 构造期 NPE（超类构造器触发监听器回调可覆写方法）</li>
 *   <li>read() 丢失列元数据导致 writeCsv 全空行</li>
 *   <li>validate/describe/pivot 在 JDBC 引擎上抛 UnsupportedOperationException</li>
 *   <li>SqliteSqlDialect 缺 formatRandom 覆盖导致 sample(seed) 生成非法 RAND()</li>
 * </ul>
 */
class JdbcEngineRegressionTest {

    private final DatasourceRegistry registry = DatasourceRegistry.getDefault();
    private CmdDatasource ds;
    private ExecutionEngine engine;

    @BeforeEach
    void setUp() {
        ds = registry.create("H2", Map.of());
        engine = registry.createExecutionEngine(ds);
        assertThat(engine).isInstanceOf(JdbcExecutionEngine.class);
        engine.execute(new QueryOperator(ds,
                "CREATE TABLE IF NOT EXISTS t_reg_emp(id INT PRIMARY KEY, name VARCHAR(50), dept VARCHAR(20), salary DOUBLE)"));
        engine.execute(new QueryOperator(ds, "DELETE FROM t_reg_emp"));
        engine.execute(new QueryOperator(ds,
                "INSERT INTO t_reg_emp VALUES (1, 'alice', '电子', 8000), (2, 'bob', '电子', 9000), (3, 'carol', '服装', 7000)"));
        engine.execute(new QueryOperator(ds,
                "CREATE TABLE IF NOT EXISTS t_reg_dept(dept VARCHAR(20) PRIMARY KEY, mgr VARCHAR(50))"));
        engine.execute(new QueryOperator(ds, "DELETE FROM t_reg_dept"));
        engine.execute(new QueryOperator(ds,
                "INSERT INTO t_reg_dept VALUES ('电子', 'dave'), ('服装', 'erin')"));
    }

    @AfterEach
    void tearDown() {
        LogicalOperator.setGlobalListener(null);
    }

    private CmdDataframe from(String table) {
        return new CmdDataframeImpl(new FromOperator(ds, table), engine);
    }

    @Test
    void joinWithListenerDoesNotThrowNpe() {
        // 复现服务端场景：监听器在算子构造期回调 getOperatorName()
        LogicalOperator.setGlobalListener(op -> op.getOperatorName());
        CmdDataframe left = from("t_reg_emp");
        CmdDataframe right = from("t_reg_dept");
        RowDataFrame result = left.join(right, "left_tbl.dept = right_tbl.dept").collect();
        assertThat(result.rowSize()).isEqualTo(3);
    }

    @Test
    void jdbcReadPopulatesColumnMetadata() {
        RowDataFrame result = engine.execute(
                new QueryOperator(ds, "SELECT id, name FROM t_reg_emp ORDER BY id"));
        assertThat(result.getColumns()).hasSize(2);
        assertThat(result.getColumns().get(1).getColumnName()).isEqualToIgnoringCase("name");
        assertThat(result.rowSize()).isEqualTo(3);
    }

    @Test
    void validatePassesOnJdbc() {
        RowDataFrame result = from("t_reg_emp")
                .validate("salary > 0 AND name IS NOT NULL", "薪资门禁")
                .collect();
        assertThat(result.rowSize()).isEqualTo(3);
    }

    @Test
    void validateBlocksOnJdbcWithMessage() {
        engine.execute(new QueryOperator(ds,
                "INSERT INTO t_reg_emp VALUES (99, 'bad', '电子', -1)"));
        try {
            assertThatThrownBy(() -> from("t_reg_emp")
                    .validate("salary > 0", "薪资门禁拦截")
                    .collect())
                    .isInstanceOf(DataQualityException.class)
                    .hasMessageContaining("薪资门禁拦截");
        } finally {
            engine.execute(new QueryOperator(ds, "DELETE FROM t_reg_emp WHERE id = 99"));
        }
    }

    @Test
    void describeWorksOnJdbc() {
        RowDataFrame result = from("t_reg_emp").describe().collect();
        assertThat(result.rowSize()).isGreaterThan(0);
        assertThat(result.getColumns()).isNotEmpty();
    }

    @Test
    void pivotWorksOnJdbcWithChineseAlias() {
        RowDataFrame result = from("t_reg_emp")
                .pivot("dept", "salary", "SUM", "dept")
                .collect();
        assertThat(result.rowSize()).isEqualTo(2);
        boolean hasChinese = result.getColumns().stream()
                .anyMatch(c -> "电子".equals(c.getColumnName()));
        assertThat(hasChinese).as("pivot 应保留中文别名而非洗成下划线").isTrue();
    }

    @Test
    void sqliteFormatRandomUsesRandomFunction() {
        assertThat(new SqliteSqlDialect().formatRandom(null)).isEqualTo("RANDOM()");
        assertThat(new SqliteSqlDialect().formatRandom(42L)).isEqualTo("RANDOM()");
    }
}
