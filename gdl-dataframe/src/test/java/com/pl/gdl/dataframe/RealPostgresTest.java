package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.dataframe.engine.JdbcExecutionEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 真实 PostgreSQL 集成测试：使用开源 embedded-postgres 启动真实 PG Server，
 * 验证 Provider 全链路（建表/查询/连接池/健康检查/元数据/方言下推）。
 */
public class RealPostgresTest {

    private static EmbeddedPostgres postgres;
    private static PostgresDatasource ds;
    private static final DatasourceRegistry registry = DatasourceRegistry.getDefault();

    @BeforeAll
    public static void startPostgres() throws Exception {
        try {
            postgres = EmbeddedPostgres.start();
        } catch (Exception e) {
            // 受限环境（如以 root 运行、网络沙箱）无法启动嵌入式 PG 时跳过，
            // CI（GitHub Actions）中正常执行
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "EmbeddedPostgres 无法启动，跳过真实 PG 测试: " + e.getMessage());
        }
        int port = postgres.getPort();
        ds = (PostgresDatasource) registry.create("POSTGRES", Map.of(
                "host", "localhost", "port", port, "database", "postgres",
                "username", "postgres", "password", "postgres"));
        assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:" + port + "/postgres");
    }

    @AfterAll
    public static void stopPostgres() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    public void realQueryViaProvider() {
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        assertThat(engine).isInstanceOf(JdbcExecutionEngine.class);

        engine.execute(new QueryOperator(ds, "CREATE TABLE t_emp(id SERIAL PRIMARY KEY, name TEXT, salary NUMERIC(10,2))"));
        engine.execute(new QueryOperator(ds, "INSERT INTO t_emp(name, salary) VALUES ('alice', 8000.00), ('bob', 9500.50)"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT name, salary FROM t_emp ORDER BY id"));
        assertThat(result.getRows()).hasSize(2);
        assertThat((Object) result.getRows().get(0).getValue("name")).isEqualTo("alice");
        assertThat(((Number) result.getRows().get(1).getValue("salary")).doubleValue())
                .isEqualTo(9500.50, within(0.001));
    }

    @Test
    public void realHealthAndMetadata() {
        DatasourceHealth health = registry.health(ds);
        assertThat(health.healthy()).isTrue();
        assertThat(health.productName()).containsIgnoringCase("PostgreSQL");

        var snapshot = registry.inspect(ds);
        assertThat(snapshot).isNotNull();
    }

    @Test
    public void realConnectionPool() throws Exception {
        try (Connection conn = JdbcConnectionManager.getDefault().getConnection(ds)) {
            assertThat(conn.isValid(5)).isTrue();
            try (Statement st = conn.createStatement()) {
                st.execute("SELECT 1");
            }
        }
    }

    @Test
    public void postgresDialectPushdown() {
        // PG 特有语法：RETURNING、ILIKE、ON CONFLICT
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        engine.execute(new QueryOperator(ds, "CREATE TABLE IF NOT EXISTS t_kv(k TEXT PRIMARY KEY, v INT)"));
        engine.execute(new QueryOperator(ds,
                "INSERT INTO t_kv(k, v) VALUES ('a', 1) ON CONFLICT (k) DO UPDATE SET v = EXCLUDED.v"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT v FROM t_kv WHERE k ILIKE 'A'"));
        assertThat(result.getRows()).hasSize(1);
        assertThat(((Number) result.getRows().get(0).getValue("v")).intValue()).isEqualTo(1);
    }
}
