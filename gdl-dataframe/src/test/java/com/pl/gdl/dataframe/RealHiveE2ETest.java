package com.pl.gdl.dataframe;

import com.pl.gdl.dataframe.datasource.*;
import org.junit.jupiter.api.*;

import java.sql.*;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Hive 真实全流程测试：通过 Hive JDBC 直连真实 HiveServer2，
 * 执行完整的建表/插入/查询/聚合/覆盖写/删除流程。
 *
 * 运行方式：
 * 1. 启动 HiveServer2（本地或 Docker）
 * 2. 设置环境变量 HIVE_TEST_URL（如 jdbc:hive2://127.0.0.1:10000/default）
 * 3. 运行：mvn test -Dtest=RealHiveE2ETest
 *
 * 未设置 HIVE_TEST_URL 时自动跳过。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealHiveE2ETest {

    private static final String HIVE_URL = System.getenv("HIVE_TEST_URL");
    private static final String USER = System.getenv().getOrDefault("HIVE_TEST_USER", "");
    private static final String PASSWORD = System.getenv().getOrDefault("HIVE_TEST_PASSWORD", "");
    private static final String TABLE = "gdl_e2e_test";

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        assumeTrue(HIVE_URL != null && !HIVE_URL.isBlank(),
                "未设置 HIVE_TEST_URL，跳过真实 Hive 测试");
        Class.forName("org.apache.hive.jdbc.HiveDriver");
        conn = DriverManager.getConnection(HIVE_URL, USER, PASSWORD);
        assertThat(conn).isNotNull();
        System.out.println("Hive 连接成功: " + HIVE_URL);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE);
            }
            conn.close();
            System.out.println("Hive 连接已关闭，测试表已清理");
        }
    }

    @Test
    @Order(1)
    public void hiveProviderCreatesJdbcDatasource() {
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        CmdDatasource ds = registry.create("HIVE", Map.of("url", HIVE_URL));
        assertThat(ds).isInstanceOf(HiveJdbcDatasource.class);
        assertThat(ds).isInstanceOf(JdbcDatasource.class);
        HiveJdbcDatasource hiveDs = (HiveJdbcDatasource) ds;
        assertThat(hiveDs.getJdbcUrl()).isEqualTo(HIVE_URL);
        assertThat(hiveDs.getDriverClassName()).isEqualTo("org.apache.hive.jdbc.HiveDriver");
        // 有 URL 时应能创建执行引擎
        assertThat(registry.require("HIVE").createExecutionEngine(ds)).isNotNull();
        System.out.println("HiveJdbcDatasource 创建成功: " + ds);
    }

    @Test
    @Order(2)
    public void hiveCreateTableAndInsert() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + TABLE);
            stmt.execute("CREATE TABLE " + TABLE + " (id INT, name STRING, amount DOUBLE) STORED AS TEXTFILE");
            System.out.println("Hive 建表成功: " + TABLE);

            stmt.execute("INSERT INTO TABLE " + TABLE +
                    " VALUES (1, 'alice', 100.5), (2, 'bob', 200.75), (3, 'carol', 300.25)");
            System.out.println("Hive 插入数据成功");
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + TABLE + " ORDER BY id")) {
            int count = 0;
            while (rs.next()) {
                count++;
                if (count == 1) {
                    assertThat(rs.getInt("id")).isEqualTo(1);
                    assertThat(rs.getString("name")).isEqualTo("alice");
                    assertThat(rs.getDouble("amount")).isEqualTo(100.5);
                }
            }
            assertThat(count).isEqualTo(3);
            System.out.println("Hive 查询验证通过: " + count + " 行");
        }
    }

    @Test
    @Order(3)
    public void hiveAggregation() throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) as cnt, SUM(amount) as total FROM " + TABLE)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt("cnt")).isEqualTo(3);
            assertThat(rs.getDouble("total")).isCloseTo(601.5, org.assertj.core.data.Offset.offset(0.01));
            System.out.println("Hive 聚合查询通过: count=3, total=" + rs.getDouble("total"));
        }
    }

    @Test
    @Order(4)
    public void hiveInsertOverwrite() throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT OVERWRITE TABLE " + TABLE + " VALUES (10, 'dave', 999.99)");
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + TABLE)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("dave");
            assertThat(rs.next()).isFalse();
            System.out.println("Hive INSERT OVERWRITE 通过");
        }
    }

    @Test
    @Order(5)
    public void hiveTableMetadata() throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, TABLE, null)) {
            int colCount = 0;
            while (rs.next()) {
                colCount++;
                System.out.println("  列: " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
            }
            assertThat(colCount).isEqualTo(3);
        }
        System.out.println("Hive 元数据查询通过");
    }
}
