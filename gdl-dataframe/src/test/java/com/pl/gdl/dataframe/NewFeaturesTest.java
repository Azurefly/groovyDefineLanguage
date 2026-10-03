package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.dataframe.ExecutionMetrics;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新功能测试：采样、数据质量检查、数据探查、缓存、透视表。
 */
public class NewFeaturesTest {

    private CmdDataframe createTestData(InMemoryEngine engine) {
        RowDataFrame df = new RowDataFrame(List.of(
                new ColumnInfo("id", "INT"),
                new ColumnInfo("name", "STRING"),
                new ColumnInfo("amount", "DOUBLE")));
        for (int i = 1; i <= 100; i++) {
            df.addRowValue(List.of(i, "user" + i, (double) i * 10));
        }
        engine.registerTable("t_test", df);
        return new CmdDataframeImpl(new FromOperator(new HiveDatasource(), "t_test"), engine);
    }

    @Test
    public void testSampleBySize() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        RowDataFrame result = df.sample(10).collect();
        assertThat(result.rowSize()).isEqualTo(10);
    }

    @Test
    public void testSampleByFraction() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        RowDataFrame result = df.sample(1.0).collect();
        // fraction=1.0 时 RAND() < 1.0 恒成立，应返回全部
        assertThat(result.rowSize()).isEqualTo(100);
    }

    @Test
    public void testSampleInvalidArgs() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        assertThatThrownBy(() -> df.sample(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> df.sample(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> df.sample(0.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> df.sample(1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testSampleSqlGeneration() {
        HiveDatasource hiveDs = new HiveDatasource("test-conf");
        com.pl.gdl.dataframe.engine.SqlPushdownEngine engine = new com.pl.gdl.dataframe.engine.SqlPushdownEngine();

        CmdDataframe df = new CmdDataframeImpl(new FromOperator(hiveDs, "dw.t_user"), engine)
                .sample(100);
        String sql = engine.toSql(df.getOperator());
        assertThat(sql).contains("ORDER BY RAND()");
        assertThat(sql).contains("LIMIT 100");

        CmdDataframe df2 = new CmdDataframeImpl(new FromOperator(hiveDs, "dw.t_user"), engine)
                .sample(0.1);
        String sql2 = engine.toSql(df2.getOperator());
        assertThat(sql2).contains("RAND() < 0.1");
    }

    @Test
    public void testValidatePass() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        // 所有 amount > 0，检查通过，返回原数据
        RowDataFrame result = df.validate("amount > 0", "金额必须为正数").collect();
        assertThat(result.rowSize()).isEqualTo(100);
    }

    @Test
    public void testValidateFail() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        // amount 最大为 1000，检查 amount > 2000 应失败
        assertThatThrownBy(() -> df.validate("amount > 2000", "金额必须大于2000").collect())
                .isInstanceOf(com.pl.gdl.dataframe.operator.base.DataQualityException.class)
                .hasMessageContaining("金额必须大于2000")
                .hasMessageContaining("违规行数: 100");
    }

    @Test
    public void testValidateInvalidArgs() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        assertThatThrownBy(() -> df.validate("", "msg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> df.validate(null, "msg")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testDescribe() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        RowDataFrame result = df.describe().collect();
        // 3 列：id, name, amount
        assertThat(result.rowSize()).isEqualTo(3);

        // 找到 id 列的统计
        com.pl.gdl.common.model.Row idRow = null;
        for (int i = 0; i < result.rowSize(); i++) {
            if ("id".equals(result.getRow(i).getValue("column_name"))) {
                idRow = result.getRow(i);
                break;
            }
        }
        assertThat(idRow).isNotNull();
        assertThat(((Number) idRow.getValue("row_count")).longValue()).isEqualTo(100);
        assertThat(((Number) idRow.getValue("null_count")).longValue()).isEqualTo(0);
        assertThat(((Number) idRow.getValue("distinct_count")).longValue()).isEqualTo(100);
    }

    @Test
    public void testCache() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        assertThat(df.isCached()).isFalse();
        df.cache();
        assertThat(df.isCached()).isTrue();

        // 缓存后 collect 返回相同数据
        RowDataFrame r1 = df.collect();
        RowDataFrame r2 = df.collect();
        assertThat(r1.rowSize()).isEqualTo(r2.rowSize()).isEqualTo(100);

        df.uncache();
        assertThat(df.isCached()).isFalse();
        // uncache 后重新计算，结果一致
        assertThat(df.collect().rowSize()).isEqualTo(100);
    }

    @Test
    public void testPivot() {
        InMemoryEngine engine = new InMemoryEngine();
        RowDataFrame df = new RowDataFrame(List.of(
                new ColumnInfo("id", "INT"),
                new ColumnInfo("quarter", "STRING"),
                new ColumnInfo("amount", "DOUBLE")));
        df.addRowValue(List.of(1, "Q1", 100.0));
        df.addRowValue(List.of(1, "Q2", 200.0));
        df.addRowValue(List.of(2, "Q1", 150.0));
        df.addRowValue(List.of(2, "Q2", 250.0));
        engine.registerTable("t_pivot", df);

        CmdDataframe cdf = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_pivot"), engine);
        RowDataFrame result = cdf.pivot("quarter", "amount", "SUM", "id").collect();

        assertThat(result.rowSize()).isEqualTo(2);
        // 验证列名包含 Q1, Q2
        boolean hasQ1 = false, hasQ2 = false;
        for (ColumnInfo col : result.getColumns()) {
            if ("Q1".equals(col.getColumnName())) hasQ1 = true;
            if ("Q2".equals(col.getColumnName())) hasQ2 = true;
        }
        assertThat(hasQ1).isTrue();
        assertThat(hasQ2).isTrue();

        // 验证 id=1 的 Q1=100, Q2=200
        for (int i = 0; i < result.rowSize(); i++) {
            Object idVal = result.getRow(i).getValue("id");
            if (idVal != null && ((Number) idVal).intValue() == 1) {
                assertThat(((Number) result.getRow(i).getValue("Q1")).doubleValue()).isEqualTo(100.0);
                assertThat(((Number) result.getRow(i).getValue("Q2")).doubleValue()).isEqualTo(200.0);
            }
        }
    }

    @Test
    public void testPivotInvalidArgs() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine);

        assertThatThrownBy(() -> df.pivot("", "amount", "SUM", "id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> df.pivot("quarter", "", "SUM", "id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testWriteCsvAndJson() throws Exception {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine).limit(5);

        java.nio.file.Path csvPath = java.nio.file.Files.createTempFile("gdl-test-", ".csv");
        java.nio.file.Path jsonPath = java.nio.file.Files.createTempFile("gdl-test-", ".json");
        try {
            df.writeCsv(csvPath.toString());
            java.util.List<String> csvLines = java.nio.file.Files.readAllLines(csvPath);
            // 表头 + 5 行数据（H2 列名大写）
            assertThat(csvLines).hasSize(6);
            assertThat(csvLines.get(0).toLowerCase()).contains("id").contains("name").contains("amount");

            df.writeJson(jsonPath.toString());
            java.util.List<String> jsonLines = java.nio.file.Files.readAllLines(jsonPath);
            assertThat(jsonLines).hasSize(5);
            assertThat(jsonLines.get(0).toLowerCase()).contains("\"id\"");
        } finally {
            java.nio.file.Files.deleteIfExists(csvPath);
            java.nio.file.Files.deleteIfExists(jsonPath);
        }
    }

    @Test
    public void testLineage() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine)
                .where("amount > 100")
                .select("id", "amount");

        java.util.List<String> lineage = df.lineage();
        assertThat(lineage).isNotEmpty();
        // 按执行顺序：from -> where -> select
        assertThat(lineage.get(0)).contains("from");
        assertThat(lineage).anyMatch(s -> s.contains("where"));
        assertThat(lineage).anyMatch(s -> s.contains("select"));
    }

    @Test
    public void testExecutionMetrics() {
        InMemoryEngine engine = new InMemoryEngine();
        CmdDataframe df = createTestData(engine).where("amount > 100");

        assertThat(df.getLastMetrics()).isNull();
        RowDataFrame result = df.collect();
        ExecutionMetrics metrics = df.getLastMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getRowCount()).isEqualTo(result.rowSize());
        assertThat(metrics.getElapsedMillis()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.isFromCache()).isFalse();

        // 第二次 collect 命中缓存
        df.collect();
        assertThat(df.getLastMetrics().isFromCache()).isTrue();
    }

    @Test
    public void testRegisterFunction() {
        InMemoryEngine engine = new InMemoryEngine();
        engine.registerFunction("test_double_it", NewFeaturesTest.class, "doubleIt");

        CmdDataframe df = createTestData(engine);
        RowDataFrame result = df.withColumn("double_amount", "test_double_it(amount)").limit(3).collect();
        assertThat(result.rowSize()).isEqualTo(3);
        // 验证函数生效：amount=10 -> 20
        Object val = result.getRow(0).getValue("double_amount");
        assertThat(((Number) val).doubleValue()).isEqualTo(20.0);
    }

    /** 测试用 UDF：数值翻倍 */
    public static Double doubleIt(Double x) {
        return x == null ? null : x * 2;
    }
}
