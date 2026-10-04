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
 *   <li>validate/describe/pivot 在 JDBC 引擎上抛 UnsupportedOperationException
 *      （链尾走混合执行，链中走临时表物化）</li>
 *   <li>SqliteSqlDialect 缺 formatRandom 覆盖导致 sample(seed) 生成非法 RAND()</li>
 *   <li>列名大小写跨方言不一致（H2 大写折叠）：JDBC 引擎统一转小写</li>
 *   <li>SQLite 不支持 seed 随机：sample(seed) 走内存确定性采样</li>
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

    @Test
    void validateMidChainWorksOnJdbc() {
        // validate 在链中间：后接 limit/select，必须透传上游数据
        RowDataFrame result = from("t_reg_emp")
                .validate("salary > 0", "薪资门禁")
                .limit(2)
                .select("id", "name")
                .collect();
        assertThat(result.rowSize()).isEqualTo(2);
        assertThat(result.getColumns().get(0).getColumnName()).isEqualTo("id");
    }

    @Test
    void validateMidChainBlocksOnJdbc() {
        engine.execute(new QueryOperator(ds,
                "INSERT INTO t_reg_emp VALUES (98, 'bad2', '电子', -5)"));
        try {
            assertThatThrownBy(() -> from("t_reg_emp")
                    .validate("salary > 0", "链中门禁拦截")
                    .limit(2)
                    .select("id")
                    .collect())
                    .isInstanceOf(DataQualityException.class)
                    .hasMessageContaining("链中门禁拦截");
        } finally {
            engine.execute(new QueryOperator(ds, "DELETE FROM t_reg_emp WHERE id = 98"));
        }
    }

    @Test
    void pivotMidChainWorksOnJdbc() {
        // pivot 在链中间：后接 sort，透视结果物化为临时表后继续 SQL
        RowDataFrame result = from("t_reg_emp")
                .pivot("dept", "salary", "SUM", "dept")
                .sort("dept")
                .collect();
        assertThat(result.rowSize()).isEqualTo(2);
        // sort 在 pivot 之后生效：两行按 dept 有序（不硬编码中文排序规则）
        String d0 = String.valueOf((Object) result.getRow(0).getValue("dept"));
        String d1 = String.valueOf((Object) result.getRow(1).getValue("dept"));
        assertThat(d0.compareTo(d1) <= 0).as("pivot 后的 sort 应生效").isTrue();
        assertThat(java.util.Set.of(d0, d1)).containsExactlyInAnyOrder("电子", "服装");
    }

    @Test
    void describeMidChainWorksOnJdbc() {
        RowDataFrame result = from("t_reg_emp")
                .describe()
                .limit(2)
                .collect();
        assertThat(result.rowSize()).isEqualTo(2);
    }

    @Test
    void jdbcColumnLabelsAreLowercased() {
        // H2 把未加引号的别名折叠为大写，引擎统一转小写以跨源一致
        RowDataFrame result = engine.execute(
                new QueryOperator(ds, "SELECT id AS MyId, salary AS TOTAL FROM t_reg_emp WHERE id = 1"));
        assertThat(result.getColumns().get(0).getColumnName()).isEqualTo("myid");
        assertThat(result.getColumns().get(1).getColumnName()).isEqualTo("total");
        assertThat((Object) result.getRow(0).getValue("myid")).isEqualTo(1);
    }

    @Test
    void sqliteSeededSampleIsDeterministic() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("gdl-seed-test", ".db");
        try {
            com.pl.gdl.dataframe.datasource.SqliteDatasource sqliteDs =
                    new com.pl.gdl.dataframe.datasource.SqliteDatasource(tmp.toString());
            ExecutionEngine sqliteEngine = registry.createExecutionEngine(sqliteDs);
            sqliteEngine.execute(new QueryOperator(sqliteDs,
                    "CREATE TABLE t_s(id INTEGER PRIMARY KEY, v TEXT)"));
            StringBuilder ins = new StringBuilder("INSERT INTO t_s VALUES ");
            for (int i = 1; i <= 100; i++) {
                if (i > 1) ins.append(", ");
                ins.append("(").append(i).append(", 'v").append(i).append("')");
            }
            sqliteEngine.execute(new QueryOperator(sqliteDs, ins.toString()));
            CmdDataframe base = new CmdDataframeImpl(new FromOperator(sqliteDs, "t_s"), sqliteEngine);
            RowDataFrame first = base.sample(10, 42L).collect();
            RowDataFrame second = base.sample(10, 42L).collect();
            assertThat(first.rowSize()).isEqualTo(10);
            assertThat(second.rowSize()).isEqualTo(10);
            for (int i = 0; i < 10; i++) {
                assertThat((Object) second.getRow(i).getValue("id"))
                        .as("同一种子两次采样顺序必须一致")
                        .isEqualTo(first.getRow(i).getValue("id"));
            }
            // 不同种子大概率顺序不同（确定性机制生效的反证）
            RowDataFrame other = base.sample(10, 43L).collect();
            boolean sameOrder = true;
            for (int i = 0; i < 10; i++) {
                Object a = other.getRow(i).getValue("id");
                Object b = first.getRow(i).getValue("id");
                if (!a.equals(b)) {
                    sameOrder = false;
                    break;
                }
            }
            assertThat(sameOrder).as("不同种子应产生不同顺序").isFalse();
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    @Test
    void describePushdownMatchesInMemory() {
        // describe 下推：聚合 SQL，结果正确，且上游数据不离库（大数据安全）
        // t_reg_emp: (1,alice,电子,8000), (2,bob,电子,9000), (3,carol,服装,7000)
        RowDataFrame result = from("t_reg_emp").describe().collect();
        assertThat(result.rowSize()).isEqualTo(4);
        java.util.Map<String, com.pl.gdl.common.model.Row> byCol = new java.util.HashMap<>();
        for (int i = 0; i < result.rowSize(); i++) {
            com.pl.gdl.common.model.Row r = result.getRow(i);
            byCol.put(String.valueOf((Object) r.getValue("column_name")), r);
        }
        com.pl.gdl.common.model.Row id = byCol.get("id");
        assertThat((Object) id.getValue("row_count")).isEqualTo(3L);
        assertThat((Object) id.getValue("null_count")).isEqualTo(0L);
        assertThat((Object) id.getValue("distinct_count")).isEqualTo(3L);
        assertThat(String.valueOf((Object) id.getValue("avg_value"))).isEqualTo("2");
        com.pl.gdl.common.model.Row salary = byCol.get("salary");
        assertThat((Object) salary.getValue("distinct_count")).isEqualTo(3L);
        // avg 值按数值比较（H2 对 DOUBLE 的 AVG 可能输出科学计数法如 8E+3，旧内存版亦然）
        assertThat(Double.parseDouble(String.valueOf((Object) salary.getValue("avg_value"))))
                .isEqualTo(8000.0);
        com.pl.gdl.common.model.Row dept = byCol.get("dept");
        assertThat((Object) dept.getValue("distinct_count")).isEqualTo(2L);
        assertThat((Object) dept.getValue("avg_value")).isNull();
    }
}
