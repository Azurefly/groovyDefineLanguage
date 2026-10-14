package com.pl.gdl.ontology;

import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.datasource.JdbcDatasource;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.ontology.ddl.OntologyDdlGenerator;
import com.pl.gdl.ontology.model.Ontology;
import com.pl.gdl.ontology.registry.OntologyMetadata;
import com.pl.gdl.ontology.registry.OntologyRegistry;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本体真实验证（H2 真实数据库）：创建、CRUD、校验、关联、事务、审计、乐观锁全链路。
 */
public class OntologyE2eVerificationTest {

    // ============ 本体定义 ============

    public static class Product extends Ontology {
        @Column(cName = "product_code", primaryKey = true, nullable = false, dataLength = 32)
        public String productCode = "product_code";
        @Column(cName = "product_name", nullable = false, dataLength = 64)
        public String productName = "product_name";
        @Column(cName = "price")
        public String price = "price";
        @Column(cName = "stock")
        public String stock = "stock";
        @Column(cName = "version")
        public String version = "version";
        @Column(cName = "created_by")
        public String createdBy = "created_by";
        @Column(cName = "created_time")
        public String createdTime = "created_time";
        @Column(cName = "updated_by")
        public String updatedBy = "updated_by";
        @Column(cName = "updated_time")
        public String updatedTime = "updated_time";
        @Column(cName = "deleted")
        public String deleted = "deleted";

        public Product() {
            oId = "e2e.product";
            oName = "Product";
            oDesc = "e2e product";
            oAuthor = "verifier";
            oTable = "t_e2e_product";
        }
    }

    public static class PurchaseOrder extends Ontology {
        public PurchaseOrder() {
            oId = "e2e.order";
            oName = "PurchaseOrder";
            oDesc = "e2e order";
            oAuthor = "verifier";
            oTable = "t_e2e_order";
        }
    }

    public static class OrderItem extends Ontology {
        public OrderItem() {
            oId = "e2e.item";
            oName = "OrderItem";
            oDesc = "e2e item";
            oAuthor = "verifier";
            oTable = "t_e2e_item";
        }
    }

    public static class Student extends Ontology {
        public Student() {
            oId = "e2e.student";
            oName = "Student";
            oDesc = "e2e student";
            oAuthor = "verifier";
            oTable = "t_e2e_student";
        }
    }

    public static class Course extends Ontology {
        public Course() {
            oId = "e2e.course";
            oName = "Course";
            oDesc = "e2e course";
            oAuthor = "verifier";
            oTable = "t_e2e_course";
        }
    }

    private static CmdDatasource ds;

    @BeforeAll
    static void setup() throws Exception {
        ds = DatasourceRegistry.getDefault().create("H2", Map.of());
        ExecutionEngine engine = DatasourceRegistry.getDefault().createExecutionEngine(ds);
        GdlExecutionContext ctx = new GdlExecutionContext();
        ctx.setExecutionEngine(engine);
        GdlExecutionContext.set(ctx);
        Ontology.setDefaultDatasource(ds);
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS t_e2e_product");
            st.execute("CREATE TABLE t_e2e_product (product_code VARCHAR(32) PRIMARY KEY, product_name VARCHAR(64) NOT NULL, price DECIMAL(10,2), stock INT, version INT DEFAULT 0, created_by VARCHAR(64), created_time TIMESTAMP, updated_by VARCHAR(64), updated_time TIMESTAMP, deleted INT DEFAULT 0)");
            st.execute("DROP TABLE IF EXISTS t_e2e_order");
            st.execute("CREATE TABLE t_e2e_order (id INT PRIMARY KEY, customer VARCHAR(64), deleted INT DEFAULT 0)");
            st.execute("DROP TABLE IF EXISTS t_e2e_item");
            st.execute("CREATE TABLE t_e2e_item (id INT PRIMARY KEY, order_id INT, item VARCHAR(64), qty INT)");
            st.execute("DROP TABLE IF EXISTS t_e2e_student");
            st.execute("CREATE TABLE t_e2e_student (id INT PRIMARY KEY, name VARCHAR(64))");
            st.execute("DROP TABLE IF EXISTS t_e2e_course");
            st.execute("CREATE TABLE t_e2e_course (id INT PRIMARY KEY, title VARCHAR(64))");
            st.execute("DROP TABLE IF EXISTS t_e2e_enrollment");
            st.execute("CREATE TABLE t_e2e_enrollment (student_id INT, course_id INT, PRIMARY KEY (student_id, course_id))");
        }
    }

    // ============ 1. 本体创建 ============

    @Test
    void verify_registration_byClass() {
        var rsp = OntologyRegistry.getInstance().registerOntology(Product.class);
        assertThat(rsp.getStatus()).isEqualTo(RegisterRsp.STATUS_SUCCESS);
        // 重复注册应失败
        var rsp2 = OntologyRegistry.getInstance().registerOntology(Product.class);
        assertThat(rsp2.getStatus()).isEqualTo(RegisterRsp.STATUS_FAILED);
        OntologyRegistry.getInstance().unregisterOntology(Product.class.getName());
    }

    @Test
    void verify_ddl_usesOTable() {
        var meta = new OntologyMetadata(Product.class);
        String ddl = OntologyDdlGenerator.generateHiveDdl(meta);
        assertThat(ddl).contains("t_e2e_product");
        assertThat(ddl).contains("product_code");
    }

    @Test
    void verify_addColumns_and_dropTable() throws Exception {
        var p = new Product();
        p.addColumns("temp_note VARCHAR(128)");
        // 验证列已添加
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var rs = c.getMetaData().getColumns(null, null, "T_E2E_PRODUCT", "TEMP_NOTE")) {
            assertThat(rs.next()).isTrue();
        }
        // 清理：重建表（drop temp_note 列需要 H2 支持 DROP COLUMN）
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var st = c.createStatement()) {
            st.execute("ALTER TABLE t_e2e_product DROP COLUMN temp_note");
        }
    }

    // ============ 2. CRUD ============

    @Test
    void verify_crud_fullCycle() {
        var p = new Product();
        // Create
        p.save(new LinkedHashMap<>(Map.of("productCode", "P001", "productName", "铣刀", "price", 100, "stock", 50)));
        assertThat(p.count()).isEqualTo(1);
        // Read
        var rows = p.where("product_code = 'P001'").fetch();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("product_name")).isEqualTo("铣刀");
        // Update
        p.update("product_code = 'P001'", new LinkedHashMap<>(Map.of("price", 120)));
        var rows2 = p.reset().where("product_code = 'P001'").fetch();
        assertThat(rows2.get(0).get("price")).isEqualTo(new java.math.BigDecimal("120.00"));
        // Soft delete
        p.delete("product_code = 'P001'");
        assertThat(p.reset().count()).isEqualTo(0); // 默认过滤已删除
        assertThat(p.includeDeleted().count()).isEqualTo(1); // 包含已删除
        // Force delete
        p.forceDelete("product_code = 'P001'");
        assertThat(p.includeDeleted().reset().count()).isEqualTo(0);
    }

    @Test
    void verify_upsert() {
        var p = new Product();
        assertThat(p.upsert(Map.of("productCode", "U001", "productName", "钻头", "price", 50), "productCode"))
                .isEqualTo("insert");
        assertThat(p.upsert(Map.of("productCode", "U001", "productName", "钻头+", "price", 60), "productCode"))
                .isEqualTo("update");
        var rows = p.reset().where("product_code = 'U001'").fetch();
        assertThat(rows.get(0).get("product_name")).isEqualTo("钻头+");
        p.forceDelete("product_code = 'U001'");
    }

    @Test
    void verify_saveBatch() {
        var p = new Product();
        var r1 = new LinkedHashMap<String, Object>(Map.of("productCode", "B001", "productName", "刀1", "price", 10));
        var r2 = new LinkedHashMap<String, Object>(Map.of("productCode", "B002", "productName", "刀2", "price", 20));
        int[] affected = p.saveBatch(List.of(r1, r2));
        assertThat(affected).hasSize(2);
        assertThat(p.reset().count()).isEqualTo(2);
        p.forceDelete("product_code IN ('B001','B002')");
    }

    // ============ 3. 校验 ============

    @Test
    void verify_validation() {
        var p = new Product();
        // nullable 违反
        var errors = p.validate(Map.of("productCode", "V001"));
        assertThat(errors).anyMatch(e -> e.contains("productName"));
        // dataLength 违反
        var errors2 = p.validate(Map.of("productCode", "V001", "productName", "x".repeat(100)));
        assertThat(errors2).anyMatch(e -> e.contains("长度超限"));
        // validateOrThrow
        p.oValidateOnWrite = true;
        assertThatThrownBy(() -> p.save(new LinkedHashMap<>(Map.of("productCode", "V002"))))
                .isInstanceOf(com.pl.gdl.common.exception.OntologyValidationException.class);
        p.oValidateOnWrite = false;
    }

    // ============ 4. 关联 ============

    @Test
    void verify_hasMany() {
        var order = new PurchaseOrder();
        var item = new OrderItem();
        order.hasMany("items", item, "order_id", "id");
        order.save(new LinkedHashMap<>(Map.of("id", 1, "customer", "张三")));
        item.save(new LinkedHashMap<>(Map.of("id", 101, "order_id", 1, "item", "铣刀", "qty", 2)));
        item.save(new LinkedHashMap<>(Map.of("id", 102, "order_id", 1, "item", "钻头", "qty", 3)));

        var rows = order.with("items").fetch();
        assertThat(rows).hasSize(1);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) rows.get(0).get("items");
        assertThat(items).hasSize(2);

        order.forceDelete("id = 1");
        item.forceDelete("order_id = 1");
    }

    @Test
    void verify_belongsTo() {
        var order = new PurchaseOrder();
        var item = new OrderItem();
        item.belongsTo("order", order, "order_id", "id");
        order.save(new LinkedHashMap<>(Map.of("id", 2, "customer", "李四")));
        item.save(new LinkedHashMap<>(Map.of("id", 201, "order_id", 2, "item", "卡尺", "qty", 1)));

        var rows = item.with("order").fetch();
        assertThat(rows).hasSize(1);
        @SuppressWarnings("unchecked")
        var parent = (Map<String, Object>) rows.get(0).get("order");
        assertThat(parent).isNotNull();
        assertThat(parent.get("customer")).isEqualTo("李四");

        order.forceDelete("id = 2");
        item.forceDelete("id = 201");
    }

    @Test
    void verify_manyToMany() throws Exception {
        var student = new Student();
        var course = new Course();
        student.manyToMany("courses", course, "t_e2e_enrollment", "student_id", "course_id");
        student.save(new LinkedHashMap<>(Map.of("id", 1, "name", "小明")));
        course.save(new LinkedHashMap<>(Map.of("id", 11, "title", "数学")));
        course.save(new LinkedHashMap<>(Map.of("id", 12, "title", "语文")));
        // 中间表
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var st = c.createStatement()) {
            st.execute("INSERT INTO t_e2e_enrollment VALUES (1, 11), (1, 12)");
        }

        var rows = student.with("courses").fetch();
        assertThat(rows).hasSize(1);
        @SuppressWarnings("unchecked")
        var courses = (List<Map<String, Object>>) rows.get(0).get("courses");
        assertThat(courses).hasSize(2);

        student.forceDelete("id = 1");
        course.forceDelete("id IN (11, 12)");
        try (var c = JdbcConnectionManager.getDefault().getConnection((JdbcDatasource) ds);
             var st = c.createStatement()) {
            st.execute("DELETE FROM t_e2e_enrollment");
        }
    }

    // ============ 5. 事务 ============

    @Test
    void verify_transaction_commit() {
        var order = new PurchaseOrder();
        var item = new OrderItem();
        order.transaction(() -> {
            order.save(new LinkedHashMap<>(Map.of("id", 10, "customer", "王五")));
            item.save(new LinkedHashMap<>(Map.of("id", 1001, "order_id", 10, "item", "量具", "qty", 1)));
        });
        assertThat(order.reset().where("id = 10").count()).isEqualTo(1);
        order.forceDelete("id = 10");
        item.forceDelete("id = 1001");
    }

    @Test
    void verify_transaction_rollback() {
        var order = new PurchaseOrder();
        var item = new OrderItem();
        try {
            order.transaction(() -> {
                order.save(new LinkedHashMap<>(Map.of("id", 20, "customer", "赵六")));
                throw new RuntimeException("模拟业务异常");
            });
        } catch (RuntimeException ignored) {
        }
        // 回滚后应无残留
        assertThat(order.reset().where("id = 20").count()).isEqualTo(0);
    }

    // ============ 6. 审计 + 乐观锁 ============

    @Test
    void verify_auditFields() {
        var p = new Product();
        p.save(new LinkedHashMap<>(Map.of("productCode", "A001", "productName", "审计测试")));
        var rows = p.reset().where("product_code = 'A001'").fetch();
        assertThat(rows.get(0).get("created_by")).isEqualTo("verifier");
        assertThat(rows.get(0).get("created_time")).isNotNull();
        assertThat(rows.get(0).get("updated_by")).isEqualTo("verifier");
        p.forceDelete("product_code = 'A001'");
    }

    @Test
    void verify_optimisticLock() {
        var p = new Product();
        p.save(new LinkedHashMap<>(Map.of("productCode", "L001", "productName", "锁测试", "version", 0)));
        // 正常更新：version 0 -> 1
        p.update("product_code = 'L001'", new LinkedHashMap<>(Map.of("price", 99, "version", 0)));
        //  stale version 更新应抛 OptimisticLockException
        assertThatThrownBy(() ->
                p.update("product_code = 'L001'", new LinkedHashMap<>(Map.of("price", 88, "version", 0))))
                .isInstanceOf(com.pl.gdl.common.exception.OptimisticLockException.class);
        p.forceDelete("product_code = 'L001'");
    }

    // ============ 7. 生命周期钩子 ============

    /** Groovy Closure 辅助：Java 侧用匿名类构造 Closure（Closure 非函数式接口，不支持 lambda） */
    private static groovy.lang.Closure<Object> closure(java.util.function.Function<Object[], Object> fn) {
        return new groovy.lang.Closure<Object>(null) {
            @Override
            public Object call(Object... args) {
                return fn.apply(args);
            }
        };
    }

    @Test
    void verify_hooks() {
        var p = new Product();
        List<String> fired = new ArrayList<>();
        p.beforeSave(closure(args -> {
            fired.add("beforeSave");
            return true;
        }));
        p.afterSave(closure(args -> {
            fired.add("afterSave");
            return null;
        }));
        p.save(new LinkedHashMap<>(Map.of("productCode", "H001", "productName", "钩子测试")));
        assertThat(fired).containsExactly("beforeSave", "afterSave");
        p.forceDelete("product_code = 'H001'");
    }

    @Test
    void verify_hookVeto() {
        var p = new Product();
        p.beforeSave(closure(args -> false)); // 否决
        assertThatThrownBy(() ->
                p.save(new LinkedHashMap<>(Map.of("productCode", "VETO1", "productName", "否决测试"))))
                .isInstanceOf(com.pl.gdl.common.exception.HookVetoException.class);
        assertThat(p.reset().where("product_code = 'VETO1'").count()).isEqualTo(0);
    }
}
