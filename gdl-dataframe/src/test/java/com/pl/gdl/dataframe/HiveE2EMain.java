package com.pl.gdl.dataframe;

import com.pl.gdl.dataframe.datasource.*;
import java.sql.*;
import java.util.Map;

/**
 * Hive 真实全流程验证（直接运行，无需 JUnit）。
 * 用法：java -cp ... com.pl.gdl.dataframe.HiveE2EMain
 * 环境变量：HIVE_TEST_URL（如 jdbc:hive2://127.0.0.1:10000/default）
 */
public class HiveE2EMain {
    public static void main(String[] args) throws Exception {
        String url = System.getenv("HIVE_TEST_URL");
        if (url == null || url.trim().isEmpty()) {
            System.out.println("未设置 HIVE_TEST_URL，退出");
            return;
        }
        String user = System.getenv().getOrDefault("HIVE_TEST_USER", "");
        String password = System.getenv().getOrDefault("HIVE_TEST_PASSWORD", "");
        String table = "gdl_e2e_test";

        // 1. 验证 GDL 的 HiveJdbcDatasource
        DatasourceRegistry registry = DatasourceRegistry.getDefault();
        CmdDatasource ds = registry.create("HIVE", Map.of("url", url));
        System.out.println("1. GDL HiveJdbcDatasource: " + ds);
        System.out.println("   是 JdbcDatasource: " + (ds instanceof JdbcDatasource));
        System.out.println("   执行引擎: " + registry.require("HIVE").createExecutionEngine(ds).getClass().getSimpleName());

        // 2. 直连 HiveServer2
        Class.forName("org.apache.hive.jdbc.HiveDriver");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("2. Hive 连接成功: " + url);
            try (Statement stmt = conn.createStatement()) {
                // 建表
                stmt.execute("DROP TABLE IF EXISTS " + table);
                stmt.execute("CREATE TABLE " + table + " (id INT, name STRING, amount DOUBLE) STORED AS TEXTFILE");
                System.out.println("3. 建表成功: " + table);

                // 插入
                stmt.execute("INSERT INTO TABLE " + table +
                        " VALUES (1, 'alice', 100.5), (2, 'bob', 200.75), (3, 'carol', 300.25)");
                System.out.println("4. 插入数据成功");

                // 查询
                try (ResultSet rs = stmt.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        System.out.println("   行" + count + ": id=" + rs.getInt("id") +
                                ", name=" + rs.getString("name") + ", amount=" + rs.getDouble("amount"));
                    }
                    if (count != 3) throw new RuntimeException("期望3行，实际" + count + "行");
                }
                System.out.println("5. 查询验证通过");

                // 聚合
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) c, SUM(amount) s FROM " + table)) {
                    rs.next();
                    System.out.println("6. 聚合: count=" + rs.getInt("c") + ", sum=" + rs.getDouble("s"));
                    if (rs.getInt("c") != 3) throw new RuntimeException("聚合count错误");
                }

                // INSERT OVERWRITE（Hive 特有）
                stmt.execute("INSERT OVERWRITE TABLE " + table + " VALUES (10, 'dave', 999.99)");
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rs.next();
                    if (rs.getInt(1) != 1) throw new RuntimeException("OVERWRITE后应为1行");
                }
                System.out.println("7. INSERT OVERWRITE 通过");

                // 元数据
                DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, table, null)) {
                    System.out.print("8. 元数据列: ");
                    while (rs.next()) {
                        System.out.print(rs.getString("COLUMN_NAME") + " ");
                    }
                    System.out.println();
                }

                // 清理
                stmt.execute("DROP TABLE IF EXISTS " + table);
                System.out.println("9. 清理完成");
            }
        }
        System.out.println("\n=== Hive 真实全流程测试全部通过 ===");
    }
}
