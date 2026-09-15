package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.HiveDatasource;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.engine.SqlPushdownEngine;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CmdDataframeTest {

    @Test
    public void testSqlPushdownChaining() {
        HiveDatasource hiveDs = new HiveDatasource("test-conf");
        SqlPushdownEngine engine = new SqlPushdownEngine();

        CmdDataframe df = new CmdDataframeImpl(new FromOperator(hiveDs, "dw.t_user"), engine)
                .alias("u")
                .where("u.age > 18")
                .select("u.id", "u.name", "u.age")
                .sort("u.age desc")
                .limit(0, 10);

        String sql = engine.toSql(df.getOperator());
        assertThat(sql).contains("SELECT u.id, u.name, u.age");
        assertThat(sql).contains("WHERE u.age > 18");
        assertThat(sql).contains("ORDER BY u.age desc");
        assertThat(sql).contains("LIMIT 10");
    }

    @Test
    public void testJoinAndOutputOperators() {
        HiveDatasource hiveDs = new HiveDatasource();
        SqlPushdownEngine engine = new SqlPushdownEngine();

        CmdDataframe dfA = new CmdDataframeImpl(new FromOperator(hiveDs, "t_order"), engine).alias("o");
        CmdDataframe dfB = new CmdDataframeImpl(new FromOperator(hiveDs, "t_user"), engine).alias("u");

        CmdDataframe joined = dfA.leftJoin(dfB, "o.user_id = u.id")
                .select("o.order_id", "u.name")
                .to(hiveDs, "dw.t_order_user_result")
                .overwrite();

        String sql = engine.toSql(joined.getOperator());
        assertThat(sql).startsWith("INSERT OVERWRITE TABLE dw.t_order_user_result");
        assertThat(sql).contains("LEFT JOIN");
        assertThat(sql).contains("o.user_id = u.id");
    }

    @Test
    public void testInMemoryExecution() {
        InMemoryEngine engine = new InMemoryEngine();
        ColumnInfo c1 = new ColumnInfo("id", "int");
        ColumnInfo c2 = new ColumnInfo("name", "string");
        ColumnInfo c3 = new ColumnInfo("age", "int");
        RowDataFrame data = new RowDataFrame(List.of(c1, c2, c3));

        data.addRowValue(List.of("1", "Alice", "25"));
        data.addRowValue(List.of("2", "Bob", "17"));
        data.addRowValue(List.of("3", "Charlie", "30"));

        engine.registerTable("t_test_user", data);

        CmdDataframe df = new CmdDataframeImpl(new FromOperator(new HiveDatasource(), "t_test_user"), engine)
                .where("age >= 18")
                .select("name", "age")
                .sort("age desc");

        RowDataFrame result = df.collect();
        assertThat(result.rowSize()).isEqualTo(2);
        assertThat((String) result.getRow(0).getValue("name")).isEqualTo("Charlie");
        assertThat((String) result.getRow(1).getValue("name")).isEqualTo("Alice");
    }

    @Test
    public void testRegisterTableReplacesExistingSqlData() {
        InMemoryEngine engine = new InMemoryEngine();
        List<ColumnInfo> columns = List.of(
                new ColumnInfo("id", "int"),
                new ColumnInfo("name", "string")
        );

        RowDataFrame first = new RowDataFrame(columns);
        first.addRowValue(List.of("1", "Alice"));
        engine.registerTable("t_replace", first);

        RowDataFrame second = new RowDataFrame(columns);
        second.addRowValue(List.of("2", "Bob"));
        engine.registerTable("t_replace", second);

        CmdDataframe df = new CmdDataframeImpl(new FromOperator(new HiveDatasource(), "t_replace"), engine)
                .where("1 = 1")
                .select("id", "name");

        RowDataFrame result = df.collect();
        assertThat(result.rowSize()).isEqualTo(1);
        assertThat((String) result.getRow(0).getValue("id")).isEqualTo("2");
        assertThat((String) result.getRow(0).getValue("name")).isEqualTo("Bob");
    }

    @Test
    public void testRegisterTableRejectsInvalidArguments() {
        InMemoryEngine engine = new InMemoryEngine();
        RowDataFrame data = new RowDataFrame(List.of(new ColumnInfo("id", "int")));

        assertThatThrownBy(() -> engine.registerTable("   ", data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableName");
        assertThatThrownBy(() -> engine.registerTable("t_null", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("df");
    }

    @Test
    public void testSwitchingExecutionEngineInvalidatesCachedData() {
        List<ColumnInfo> columns = List.of(new ColumnInfo("name", "string"));

        InMemoryEngine firstEngine = new InMemoryEngine();
        RowDataFrame first = new RowDataFrame(columns);
        first.addRowValue(List.of("Alice"));
        firstEngine.registerTable("t_switch", first);

        InMemoryEngine secondEngine = new InMemoryEngine();
        RowDataFrame second = new RowDataFrame(columns);
        second.addRowValue(List.of("Bob"));
        secondEngine.registerTable("t_switch", second);

        CmdDataframeImpl df = new CmdDataframeImpl(
                new FromOperator(new HiveDatasource(), "t_switch"), firstEngine);

        assertThat((String) df.collect().getRow(0).getValue("name")).isEqualTo("Alice");
        df.setExecutionEngine(secondEngine);
        assertThat((String) df.collect().getRow(0).getValue("name")).isEqualTo("Bob");
    }
}
