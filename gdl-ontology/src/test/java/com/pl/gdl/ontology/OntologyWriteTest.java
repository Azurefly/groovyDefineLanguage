package com.pl.gdl.ontology;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.model.Ontology;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本体写操作测试：upsert、关系、校验（H2 真实数据库）。
 */
public class OntologyWriteTest {

    public static class Tool extends Ontology {
        @Column(cName = "tool_code", primaryKey = true, nullable = false, dataLength = 32)
        public String toolCode = "tool_code";
        @Column(cName = "tool_name", nullable = false, dataLength = 64)
        public String toolName = "tool_name";
        @Column(cName = "unit_price")
        public String unitPrice = "unit_price";

        public Tool() {
            oId = "test.tool";
            oName = "Tool";
            oDesc = "test";
            oAuthor = "tester";
            oTable = "t_tool_w";
        }
    }

    public static class Order extends Ontology {
        public Order() {
            oId = "test.order";
            oName = "Order";
            oDesc = "test";
            oAuthor = "tester";
            oTable = "t_order_w";
        }
    }

    public static class Line extends Ontology {
        public Line() {
            oId = "test.line";
            oName = "Line";
            oDesc = "test";
            oAuthor = "tester";
            oTable = "t_line_w";
        }
    }

    private CmdDatasource ds;

    @BeforeEach
    void setup() throws Exception {
        ds = DatasourceRegistry.getDefault().create("H2", Map.of());
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(ds);
        GdlExecutionContext ctx = new GdlExecutionContext();
        ctx.setExecutionEngine(engine);
        GdlExecutionContext.set(ctx);
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS t_tool_w (tool_code VARCHAR(32) PRIMARY KEY, tool_name VARCHAR(64), unit_price DECIMAL(10,2))");
            st.execute("DELETE FROM t_tool_w");
            st.execute("CREATE TABLE IF NOT EXISTS t_order_w (id INT PRIMARY KEY, customer VARCHAR(64))");
            st.execute("DELETE FROM t_order_w");
            st.execute("CREATE TABLE IF NOT EXISTS t_line_w (id INT PRIMARY KEY, order_id INT, item VARCHAR(64))");
            st.execute("DELETE FROM t_line_w");
        }
    }

    private Tool tool() {
        Tool t = new Tool();
        t.oDs = ds;
        return t;
    }

    @Test
    void upsert_insertThenUpdate() {
        Tool t = tool();
        assertThat(t.upsert(Map.of("toolCode", "T001", "toolName", "铣刀", "unitPrice", 100), "toolCode"))
                .isEqualTo("insert");
        assertThat(t.upsert(Map.of("toolCode", "T001", "toolName", "铣刀+", "unitPrice", 120), "toolCode"))
                .isEqualTo("update");
        // 验证更新生效
        t.loadData();
        var df = t.where("tool_code = 'T001'").select("tool_name").getQueryDataframe();
        Object toolName = df.collect().getRow(0).getValue("tool_name");
        assertThat(toolName).isEqualTo("铣刀+");
    }

    @Test
    void upsert_infersPrimaryKey() {
        Tool t = tool();
        // 不传 keyColumns，从 @Column(primaryKey=true) 推断
        assertThat(t.upsert(Map.of("toolCode", "T002", "toolName", "钻头"))).isEqualTo("insert");
    }

    @Test
    void validate_detectsViolations() {
        Tool t = tool();
        // toolName 为空违反 nullable=false
        List<String> errors = t.validate(Map.of("toolCode", "T003"));
        assertThat(errors.stream().anyMatch((String e) -> e.contains("toolName"))).isTrue();
        // 超长违反 dataLength
        List<String> errors2 = t.validate(Map.of("toolCode", "T003", "toolName", "x".repeat(100)));
        assertThat(errors2.stream().anyMatch((String e) -> e.contains("长度超限"))).isTrue();
    }

    @Test
    void validateOrThrow_throwsOnViolation() {
        Tool t = tool();
        t.oValidateOnWrite = true;
        assertThatThrownBy(() -> t.save(new LinkedHashMap<>(Map.of("toolCode", "T004"))))
                .isInstanceOf(com.pl.gdl.common.exception.OntologyValidationException.class);
    }

    @Test
    void defaultDatasource_fallback() {
        // oDs 未设置时，使用全局默认数据源
        Ontology.setDefaultDatasource(ds);
        try {
            Tool t = new Tool(); // oDs 为 null
            t.oDs = null;
            t.loadData(); // 不应抛错
            assertThat(t.oDataframe).isNotNull();
        } finally {
            Ontology.setDefaultDatasource(null);
        }
    }

    @Test
    void resolveDs_failFast() {
        Ontology.setDefaultDatasource(null);
        Tool t = new Tool();
        t.oDs = null;
        assertThatThrownBy(t::loadData)
                .isInstanceOf(com.pl.gdl.common.exception.GdlException.class)
                .hasMessageContaining("数据源未设置");
    }

    @Test
    void count_withDefaultDatasource() {
        // P0-1：只设默认源时 count() 不应 NPE
        Ontology.setDefaultDatasource(ds);
        try {
            Tool t = new Tool();
            t.oDs = null;
            t.save(new LinkedHashMap<>(Map.of("toolCode", "TC1", "toolName", "铣刀")));
            assertThat(t.count()).isEqualTo(1);
        } finally {
            Ontology.setDefaultDatasource(null);
        }
    }

    @Test
    void saveBatch_columnMisalignment() {
        // P0-2：第二条记录 key 顺序不同时，仍按列名对齐，不错位
        Tool t = tool();
        var r1 = new LinkedHashMap<String, Object>(Map.of("toolCode", "B1", "toolName", "刀1", "unitPrice", 10));
        var r2 = new LinkedHashMap<String, Object>();
        r2.put("unitPrice", 20);
        r2.put("toolName", "刀2");
        r2.put("toolCode", "B2");
        t.saveBatch(List.of(r1, r2));
        var df = t.where("tool_code = 'B2'").select("tool_name", "unit_price").getQueryDataframe();
        var row = df.collect().getRow(0);
        assertThat((Object) row.getValue("tool_name")).isEqualTo("刀2");
        assertThat((Object) row.getValue("unit_price")).isEqualTo(new java.math.BigDecimal("20.00"));
    }

    @Test
    void saveBatch_extraColumnThrows() {
        // P0-2：后续记录多列时抛错，不静默错位
        Tool t = tool();
        var r1 = new LinkedHashMap<String, Object>(Map.of("toolCode", "E1", "toolName", "刀"));
        var r2 = new LinkedHashMap<String, Object>(Map.of("toolCode", "E2", "toolName", "刀", "extra", "x"));
        assertThatThrownBy(() -> t.saveBatch(List.of(r1, r2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列不一致");
    }

    @Test
    void reset_clearsWhereConditions() {
        // P1-4：reset 后 where 条件不污染下一次查询
        Tool t = tool();
        t.save(new LinkedHashMap<>(Map.of("toolCode", "R1", "toolName", "铣刀")));
        t.save(new LinkedHashMap<>(Map.of("toolCode", "R2", "toolName", "钻头")));
        assertThat(t.where("tool_code = 'R1'").count()).isEqualTo(1);
        t.reset();
        assertThat(t.count()).isEqualTo(2);
    }

    @Test
    void ddl_usesOTable() {
        // P1-3：DDL 使用 oTable 而非类名
        var meta = new com.pl.gdl.ontology.registry.OntologyMetadata(Tool.class);
        String ddl = com.pl.gdl.ontology.ddl.OntologyDdlGenerator.generateHiveDdl(meta);
        assertThat(ddl).contains("t_tool_w");
    }

    @Test
    void dropTable_works() {
        // P2-5：dropTable 真实可用
        Tool t = tool();
        t.dropTable();
        // 表已删，再 save 应失败（表不存在）
        assertThatThrownBy(() -> t.save(new LinkedHashMap<>(Map.of("toolCode", "D1", "toolName", "刀"))))
                .isInstanceOf(Exception.class);
    }

    @Test
    void registerByClass() {
        var rsp = com.pl.gdl.ontology.registry.OntologyRegistry.getInstance()
                .registerOntology(Tool.class);
        assertThat(rsp.getStatus()).isEqualTo(
                com.pl.gdl.common.model.RegisterRsp.STATUS_SUCCESS);
        // 清理，避免影响其他测试
        com.pl.gdl.ontology.registry.OntologyRegistry.getInstance()
                .unregisterOntology(Tool.class.getName());
    }

    @Test
    void hasMany_fetchWithRelation() {
        Order order = new Order(); order.oDs = ds;
        Line line = new Line(); line.oDs = ds;
        order.hasMany("lines", line, "order_id", "id");
        order.save(new LinkedHashMap<>(Map.of("id", 1, "customer", "张三")));
        line.save(new LinkedHashMap<>(Map.of("id", 101, "order_id", 1, "item", "铣刀")));
        line.save(new LinkedHashMap<>(Map.of("id", 102, "order_id", 1, "item", "钻头")));

        List<Map<String, Object>> fetched = order.with("lines").fetch();
        assertThat(fetched).hasSize(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) fetched.get(0).get("lines");
        assertThat(lines).hasSize(2);
    }
}
