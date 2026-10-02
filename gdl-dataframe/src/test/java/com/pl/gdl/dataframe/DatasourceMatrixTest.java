package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.dialect.H2SqlDialect;
import com.pl.gdl.dataframe.dialect.MysqlSqlDialect;
import com.pl.gdl.dataframe.dialect.PostgresSqlDialect;
import com.pl.gdl.dataframe.dialect.SqliteSqlDialect;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.dataframe.engine.JdbcExecutionEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 多数据源矩阵真实测试：对 README 矩阵中每个数据源做真实验证。
 * H2/SQLite 做真实 JDBC 执行；PG/MySQL 无真实 Server，验证 Provider 构造、URL、方言与优雅降级。
 */
public class DatasourceMatrixTest {

    private final DatasourceRegistry registry = DatasourceRegistry.getDefault();
    private final JdbcConnectionManager connectionManager = JdbcConnectionManager.getDefault();

    // ============ H2：真实全链路 ============

    @Test
    public void h2RealJdbcQuery() {
        CmdDatasource ds = registry.create("H2", Map.of());
        assertThat(ds).isInstanceOf(H2Datasource.class);
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        assertThat(engine).isInstanceOf(JdbcExecutionEngine.class);

        // 真实建表 + 插入 + 查询
        engine.execute(new QueryOperator(ds, "CREATE TABLE IF NOT EXISTS t_user(id INT PRIMARY KEY, name VARCHAR(50))"));
        engine.execute(new QueryOperator(ds, "INSERT INTO t_user VALUES (1, 'alice'), (2, 'bob')"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT id, name FROM t_user ORDER BY id"));
        assertThat(result.getRows()).hasSize(2);
        assertThat((Object) result.getRows().get(0).getValue("name")).isEqualTo("alice");
    }

    @Test
    public void h2ConnectionPoolAndHealth() throws Exception {
        H2Datasource ds = (H2Datasource) registry.create("H2", Map.of());
        // 连接池：能拿到连接且可用
        try (Connection conn = connectionManager.getConnection(ds)) {
            assertThat(conn.isValid(5)).isTrue();
        }
        // 健康检查：真实返回 healthy
        DatasourceHealth health = registry.health(ds);
        assertThat(health.healthy()).isTrue();
        assertThat(health.productName()).containsIgnoringCase("H2");
        // 元数据快照
        var snapshot = registry.inspect(ds);
        assertThat(snapshot).isNotNull();
    }

    @Test
    public void h2DialectAndCapabilities() {
        assertThat(registry.describe("h2").dialectName()).isEqualTo("H2");
        assertThat(new H2SqlDialect().quoteIdentifier("my table")).isEqualTo("\"my table\"");
        assertThat(registry.capabilities("h2"))
                .contains(DatasourceCapability.READ, DatasourceCapability.SQL,
                        DatasourceCapability.JDBC, DatasourceCapability.HEALTH_CHECK);
    }

    // ============ SQLite：真实全链路 ============

    @Test
    public void sqliteMemoryRealJdbcQuery() {
        CmdDatasource ds = registry.create("SQLITE", Map.of("path", ":memory:"));
        assertThat(ds).isInstanceOf(SqliteDatasource.class);
        ExecutionEngine engine = registry.createExecutionEngine(ds);

        engine.execute(new QueryOperator(ds, "CREATE TABLE t_item(id INTEGER PRIMARY KEY, price REAL)"));
        engine.execute(new QueryOperator(ds, "INSERT INTO t_item VALUES (1, 9.9), (2, 19.9)"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT SUM(price) AS total FROM t_item"));
        assertThat(result.getRows()).hasSize(1);
        assertThat(((Number) result.getRows().get(0).getValue("total")).doubleValue())
                .isEqualTo(29.8, within(0.001));
    }

    @Test
    public void sqliteFileRealJdbcQuery(@TempDir Path tempDir) throws Exception {
        String dbPath = tempDir.resolve("test.db").toString();
        SqliteDatasource ds = (SqliteDatasource) registry.create("SQLITE", Map.of("path", dbPath));
        assertThat(ds.getJdbcUrl()).isEqualTo("jdbc:sqlite:" + dbPath);

        try (Connection conn = connectionManager.getConnection(ds);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t_log(msg TEXT)");
            st.execute("INSERT INTO t_log VALUES ('hello')");
        }
        // 文件库：新连接能读到数据（验证持久化）
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT msg FROM t_log"));
        assertThat(result.getRows()).hasSize(1);
        assertThat((Object) result.getRows().get(0).getValue("msg")).isEqualTo("hello");

        DatasourceHealth health = registry.health(ds);
        assertThat(health.healthy()).isTrue();
        assertThat(health.productName()).containsIgnoringCase("SQLite");
    }

    @Test
    public void sqliteDialectAndCapabilities() {
        assertThat(registry.describe("sqlite").dialectName()).isEqualTo("SQLITE");
        assertThat(registry.capabilities("sqlite"))
                .contains(DatasourceCapability.READ, DatasourceCapability.WRITE,
                        DatasourceCapability.SQL, DatasourceCapability.JDBC,
                        DatasourceCapability.METADATA, DatasourceCapability.HEALTH_CHECK);
        assertThat(new SqliteSqlDialect().quoteIdentifier("a`b")).isEqualTo("\"a`b\"");
    }

    // ============ PostgreSQL：无真实 Server ============

    @Test
    public void postgresProviderConstruction() {
        CmdDatasource ds = registry.create("POSTGRES", Map.of(
                "host", "db.internal", "port", 5432, "database", "app",
                "username", "gdl", "password", "secret"));
        assertThat(ds).isInstanceOf(PostgresDatasource.class);
        PostgresDatasource pg = (PostgresDatasource) ds;
        assertThat(pg.getJdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:5432/app");
        assertThat(pg.getDriverClassName()).isEqualTo("org.postgresql.Driver");
        assertThat(registry.describe("postgres").dialectName()).isEqualTo("POSTGRES");
        assertThat(new PostgresSqlDialect().quoteIdentifier("my\"col")).isEqualTo("\"my\"\"col\"");
    }

    @Test
    public void postgresHealthDegradesGracefullyWithoutServer() {
        CmdDatasource ds = registry.create("POSTGRES", Map.of(
                "host", "127.0.0.1", "port", 1, "database", "x", "username", "u", "password", "p"));
        // 无真实服务时健康检查应返回 unhealthy 而不是抛异常
        DatasourceHealth health = registry.health(ds);
        assertThat(health.healthy()).isFalse();
        assertThat(health.message()).isNotBlank();
    }

    // ============ MySQL：无真实 Server ============

    @Test
    public void mysqlProviderConstruction() {
        CmdDatasource ds = registry.create("MYSQL", Map.of(
                "host", "db.internal", "port", 3306, "database", "app",
                "username", "gdl", "password", "secret"));
        assertThat(ds).isInstanceOf(MysqlDatasource.class);
        MysqlDatasource mysql = (MysqlDatasource) ds;
        assertThat(mysql.getJdbcUrl()).contains("jdbc:mysql://db.internal:3306/app");
        assertThat(mysql.getDriverClassName()).contains("mysql");
        assertThat(registry.describe("mysql").dialectName()).isEqualTo("MYSQL");
        assertThat(new MysqlSqlDialect().quoteIdentifier("a`b")).isEqualTo("`a``b`");
    }

    // ============ Hive：现有路径 ============

    @Test
    public void hiveProviderBasics() {
        CmdDatasource ds = registry.create("HIVE", Map.of());
        assertThat(ds).isInstanceOf(HiveDatasource.class);
        assertThat(ds.getDatasourceType()).isEqualTo("HIVE");
        assertThat(ds).isNotInstanceOf(JdbcDatasource.class);
    }

    // ============ LLM：非关系型 ============

    @Test
    public void llmProviderBasics() {
        CmdDatasource ds = registry.create("LLM", Map.of());
        assertThat(ds).isInstanceOf(LlmDatasource.class);
        assertThat(ds).isNotInstanceOf(JdbcDatasource.class);
    }

    // ============ 联邦：H2 + SQLite 真实跨源 ============

    @Test
    public void federatedH2AndSqlite() {
        H2Datasource h2 = (H2Datasource) registry.create("H2",
                Map.of("jdbcUrl", "jdbc:h2:mem:fed_h2;DB_CLOSE_DELAY=-1"));
        SqliteDatasource sqlite = (SqliteDatasource) registry.create("SQLITE", Map.of("path", ":memory:"));

        ExecutionEngine h2Engine = registry.createExecutionEngine(h2);
        ExecutionEngine sqliteEngine = registry.createExecutionEngine(sqlite);

        h2Engine.execute(new QueryOperator(h2, "CREATE TABLE t_order(id INT PRIMARY KEY, amount DECIMAL(10,2))"));
        h2Engine.execute(new QueryOperator(h2, "INSERT INTO t_order VALUES (1, 100.0), (2, 200.0)"));
        sqliteEngine.execute(new QueryOperator(sqlite, "CREATE TABLE t_user(id INTEGER PRIMARY KEY, name TEXT)"));
        sqliteEngine.execute(new QueryOperator(sqlite, "INSERT INTO t_user VALUES (1, 'alice'), (2, 'bob')"));

        RowDataFrame orders = h2Engine.execute(new QueryOperator(h2, "SELECT id, amount FROM t_order ORDER BY id"));
        RowDataFrame users = sqliteEngine.execute(new QueryOperator(sqlite, "SELECT id, name FROM t_user ORDER BY id"));
        assertThat(orders.getRows()).hasSize(2);
        assertThat(users.getRows()).hasSize(2);
        assertThat((Object) orders.getRows().get(0).getValue("id"))
                .isEqualTo(users.getRows().get(0).getValue("id"));
    }
}
