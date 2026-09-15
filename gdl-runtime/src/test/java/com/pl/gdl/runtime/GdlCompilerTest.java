package com.pl.gdl.runtime;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.runtime.compiler.GdlCompiler;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GdlCompilerTest {

    @Test
    public void testBasicGdlScriptExecution() {
        GdlCompiler compiler = new GdlCompiler();
        String script = """
            def hiveDs = hive()
            def df = from(hiveDs, "dw.t_person").where("age > 20").select("id, name, age")
            returnDf(df)
        """;

        GdlCompiler.GdlExecutionResult result = compiler.execute(script, Map.of());
        assertThat(result.getReturnDf()).isNotNull();

        CmdDataframe returnDf = result.getReturnDf();
        assertThat(returnDf.getOperator().getOperatorName()).isEqualTo("select");
    }

    @Test
    public void testParameterInjectionAndVariables() {
        GdlCompiler compiler = new GdlCompiler();
        String script = """
            def timeVar = variable("CurrentTimeVar", [timeVarName: "today", timeFormat: "yyyy-MM-dd"])
            def hiveDs = hive()
            def df = from(hiveDs, "dw.t_order").where("dt = '${timeVar.today}' and area_code = '${areaCode}'")
            returnDf(df)
        """;

        GdlCompiler.GdlExecutionResult result = compiler.execute(script, Map.of("areaCode", "320100"));
        assertThat(result.getReturnDf()).isNotNull();
    }

    @Test
    public void testDagSerialization() {
        GdlCompiler compiler = new GdlCompiler();
        String script = """
            def hiveDs = hive()
            def df1 = from(hiveDs, "dw.t_source").nodeId("node_1")
            def df2 = df1.where("status = 1").select("id, name").nodeId("node_2")
            df2.to(hiveDs, "dw.t_sink").nodeId("node_3")
        """;

        DagGraph dag = compiler.parseToDag(script, Map.of());
        assertThat(dag.getNodes()).isNotEmpty();

        String json = DagSerializer.toJson(dag);
        assertThat(json).contains("\"canvas\"");
        assertThat(json).contains("\"nodes\"");
    }

    @Test
    public void testGenericDatasourceDslExecutesH2Query() {
        GdlCompiler compiler = new GdlCompiler();
        String script = """
            def ds = datasource("H2", [url: "jdbc:h2:mem:dsltest;DB_CLOSE_DELAY=-1"])
            def df = query(ds, "SELECT 42 AS answer, 'multi-source' AS label")
            returnDf(df)
        """;

        GdlCompiler.GdlExecutionResult result = compiler.execute(script, Map.of());
        RowDataFrame rows = result.getReturnDf().collect();
        assertThat(rows.rowSize()).isEqualTo(1);
        assertThat((Integer) rows.getRow(0).getValue("answer")).isEqualTo(42);
        assertThat((String) rows.getRow(0).getValue("label")).isEqualTo("multi-source");
    }
}
