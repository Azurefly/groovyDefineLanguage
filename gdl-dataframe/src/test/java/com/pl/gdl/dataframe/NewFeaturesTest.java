package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
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
}
