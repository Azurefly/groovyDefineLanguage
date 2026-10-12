package com.pl.gdl.common.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {

    @Test
    public void fetchSize_defaultIs1000() {
        System.clearProperty("gdl.jdbc.fetchSize");
        assertThat(JdbcUtils.fetchSize()).isEqualTo(1000);
    }

    @Test
    public void fetchSize_respectsSystemProperty() {
        System.setProperty("gdl.jdbc.fetchSize", "500");
        try {
            assertThat(JdbcUtils.fetchSize()).isEqualTo(500);
        } finally {
            System.clearProperty("gdl.jdbc.fetchSize");
        }
    }

    @Test
    public void createStatement_appliesFetchSize() throws Exception {
        System.setProperty("gdl.jdbc.fetchSize", "777");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:jdbcut1");
             Statement stmt = JdbcUtils.createStatement(conn)) {
            assertThat(stmt.getFetchSize()).isEqualTo(777);
        } finally {
            System.clearProperty("gdl.jdbc.fetchSize");
        }
    }

    @Test
    public void createStatement_zeroMeansDriverDefault() throws Exception {
        System.setProperty("gdl.jdbc.fetchSize", "0");
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:jdbcut2");
             Statement stmt = JdbcUtils.createStatement(conn)) {
            // 0 表示不设置，不抛错即可
            assertThat(stmt).isNotNull();
        } finally {
            System.clearProperty("gdl.jdbc.fetchSize");
        }
    }
}
