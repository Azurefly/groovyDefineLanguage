package com.pl.gdl.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.JdbcConnectionManager;
import com.pl.gdl.dataframe.engine.JdbcExecutionEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.Charset;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.config.SchemaConfig;
import com.wix.mysql.distribution.Version;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;
import java.util.TimeZone;

import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 真实 MySQL 集成测试：使用开源 wix-embedded-mysql 启动真实 MySQL Server，
 * 验证 Provider 全链路（建表/查询/连接池/健康检查/元数据/方言下推）。
 */
public class RealMysqlTest {

    private static EmbeddedMysql mysql;
    private static MysqlDatasource ds;
    private static final DatasourceRegistry registry = DatasourceRegistry.getDefault();
    private static int port;

    @BeforeAll
    public static void startMysql() {
        try {
            MysqldConfig config = aMysqldConfig(Version.v8_0_11)
                    .withCharset(Charset.UTF8MB4)
                    .withPort(0) // 随机端口
                    .withTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
                    .withTimeout(60_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();
            SchemaConfig schemaConfig = SchemaConfig.aSchemaConfig("testdb")
                    .withCommands(
                            "CREATE USER IF NOT EXISTS 'testuser'@'%' IDENTIFIED BY 'testpass'",
                            "GRANT ALL PRIVILEGES ON testdb.* TO 'testuser'@'%'",
                            "FLUSH PRIVILEGES")
                    .build();
            mysql = anEmbeddedMysql(config)
                    .addSchema(schemaConfig)
                    .start();
        } catch (Exception e) {
            // 受限环境（如网络沙箱无法下载二进制）无法启动嵌入式 MySQL 时跳过，
            // CI（GitHub Actions）中正常执行
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "EmbeddedMySQL 无法启动，跳过真实 MySQL 测试: " + e.getMessage());
        }
        port = mysql.getConfig().getPort();

        ds = (MysqlDatasource) registry.create("MYSQL", Map.of(
                "host", "localhost", "port", port, "database", "testdb",
                "username", "testuser", "password", "testpass"));
        assertThat(ds.getJdbcUrl()).contains("jdbc:mysql://localhost:" + port + "/testdb");
    }

    @AfterAll
    public static void stopMysql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    public void realQueryViaProvider() {
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        assertThat(engine).isInstanceOf(JdbcExecutionEngine.class);

        engine.execute(new QueryOperator(ds, "CREATE TABLE t_product(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), price DECIMAL(10,2))"));
        engine.execute(new QueryOperator(ds, "INSERT INTO t_product(name, price) VALUES ('book', 29.99), ('pen', 3.50)"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT name, price FROM t_product ORDER BY id"));
        assertThat(result.getRows()).hasSize(2);
        assertThat((Object) result.getRows().get(0).getValue("name")).isEqualTo("book");
        assertThat(((Number) result.getRows().get(1).getValue("price")).doubleValue())
                .isEqualTo(3.50, within(0.001));
    }

    @Test
    public void realHealthAndMetadata() {
        DatasourceHealth health = registry.health(ds);
        assertThat(health.healthy()).isTrue();
        assertThat(health.productName()).containsIgnoringCase("MySQL");

        var snapshot = registry.inspect(ds);
        assertThat(snapshot).isNotNull();
    }

    @Test
    public void realConnectionPool() throws Exception {
        try (Connection conn = JdbcConnectionManager.getDefault().getConnection(ds)) {
            assertThat(conn.isValid(5)).isTrue();
        }
    }

    @Test
    public void mysqlDialectPushdown() {
        // MySQL 特有：反引号标识符、LIMIT OFFSET
        ExecutionEngine engine = registry.createExecutionEngine(ds);
        engine.execute(new QueryOperator(ds, "CREATE TABLE IF NOT EXISTS `t_limit_test` (`id` INT PRIMARY KEY, `val` VARCHAR(20))"));
        engine.execute(new QueryOperator(ds, "INSERT INTO `t_limit_test` VALUES (1, 'a'), (2, 'b'), (3, 'c')"));
        RowDataFrame result = engine.execute(new QueryOperator(ds, "SELECT `val` FROM `t_limit_test` ORDER BY `id` LIMIT 1 OFFSET 1"));
        assertThat(result.getRows()).hasSize(1);
        assertThat((Object) result.getRows().get(0).getValue("val")).isEqualTo("b");
    }
}
