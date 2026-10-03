package com.pl.gdl.dataframe;

import com.pl.gdl.dataframe.datasource.*;
import com.pl.gdl.dataframe.dialect.HiveSqlDialect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hive 测试：
 * - Provider 基础能力：Hive 是配置型数据源（非 JDBC），验证构造与能力声明
 * - 方言 SQL 生成：验证 Hive 特有语法（反引号、LIMIT OFFSET、INSERT OVERWRITE 等）
 *
 * 真实 HiveServer2 连接测试：
 * 2026-10-02 已通过真实 HiveServer2（Apache Hive 3.1.3）完成全流程验证。
 * 本地验证方式：启动 HiveServer2 后设置 HIVE_TEST_URL=jdbc:hive2://127.0.0.1:10000/default，
 * 运行 RealHiveE2ETest 或 HiveE2EMain。
 * Docker 方式：docker run -d -p 10000:10000 apache/hive:4.0.0
 */
public class RealHiveTest {

    private final DatasourceRegistry registry = DatasourceRegistry.getDefault();
    private final HiveSqlDialect dialect = new HiveSqlDialect();

    @Test
    public void hiveProviderBasics() {
        CmdDatasource ds = registry.create("HIVE", Map.of("confName", "test"));
        assertThat(ds).isInstanceOf(HiveDatasource.class);
        assertThat(ds.getDatasourceType()).isEqualTo("HIVE");
        // Hive 是配置型数据源，非 JDBC
        assertThat(ds).isNotInstanceOf(JdbcDatasource.class);
        assertThat(registry.capabilities("hive"))
                .contains(DatasourceCapability.READ, DatasourceCapability.WRITE,
                        DatasourceCapability.SQL, DatasourceCapability.PARTITIONED_WRITE);
        assertThat(registry.describe("hive").dialectName()).isEqualTo("HIVE");
    }

    @Test
    public void hiveDialectSqlGeneration() {
        // 反引号标识符（含反引号 doubling）
        assertThat(dialect.quoteIdentifier("my`table")).isEqualTo("`my``table`");
        // LIMIT：HiveQL 仅支持 LIMIT n，带 offset 应抛明确异常
        assertThat(dialect.formatLimit(0, 20)).isEqualTo("LIMIT 20");
        assertThatThrownBy(() -> dialect.formatLimit(10, 20))
                .isInstanceOf(UnsupportedOperationException.class);
        // INSERT OVERWRITE（Hive 特有）
        assertThat(dialect.formatOverwriteTable("dw.t", "SELECT 1"))
                .isEqualTo("INSERT OVERWRITE TABLE dw.t SELECT 1");
        assertThat(dialect.formatOverwritePartition("dw.t", "ds='20240101'", "SELECT 1"))
                .isEqualTo("INSERT OVERWRITE TABLE dw.t PARTITION (ds='20240101') SELECT 1");
        // concat_ws（Hive 聚合风格）
        assertThat(dialect.formatConcatWs(",", "name")).contains("concat_ws");
        // Hive 不支持 subtractAll/intersectAll（已在 SqlPushdownEngine 中 fail-fast）
    }

    @Test
    public void hiveDslHelpers() {
        // GDL 脚本层的 hive() helper 可用
        HiveDatasource ds1 = new HiveDatasource();
        assertThat(ds1.getDatasourceType()).isEqualTo("HIVE");
        HiveDatasource ds2 = new HiveDatasource("myconf");
        assertThat(ds2.toString()).contains("myconf");
    }
}
