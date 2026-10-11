package com.pl.gdl.dataframe;

import com.pl.gdl.dataframe.util.SqlSafe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQL 注入防护测试。
 */
public class SqlSafeTest {

    @Test
    void identifier_allowsValidNames() {
        assertThat(SqlSafe.identifier("age")).isEqualTo("age");
        assertThat(SqlSafe.identifier("t.col")).isEqualTo("t.col");
        assertThat(SqlSafe.identifier("_private123")).isEqualTo("_private123");
    }

    @Test
    void identifier_blocksInjection() {
        assertThatThrownBy(() -> SqlSafe.identifier("a; DROP TABLE t--"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafe.identifier("1abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafe.identifier("select"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafe.identifier(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operator_allowsWhitelist() {
        assertThat(SqlSafe.operator("=")).isEqualTo("=");
        assertThat(SqlSafe.operator("like")).isEqualTo("LIKE");
        assertThat(SqlSafe.operator("IN")).isEqualTo("IN");
    }

    @Test
    void operator_blocksInjection() {
        assertThatThrownBy(() -> SqlSafe.operator("; DELETE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlSafe.operator("OR 1=1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void literal_escapesStrings() {
        assertThat(SqlSafe.literal("O'Brien")).isEqualTo("'O''Brien'");
        assertThat(SqlSafe.literal(123)).isEqualTo("123");
        assertThat(SqlSafe.literal(true)).isEqualTo("true");
        assertThat(SqlSafe.literal(null)).isEqualTo("NULL");
    }

    @Test
    void condition_buildsSafeSql() {
        assertThat(SqlSafe.condition("age", ">", 18)).isEqualTo("age > 18");
        assertThat(SqlSafe.condition("name", "=", "张三")).isEqualTo("name = '张三'");
        assertThat(SqlSafe.condition("id", "IN", List.of(1, 2, 3))).isEqualTo("id IN (1, 2, 3)");
        assertThat(SqlSafe.condition("deleted", "IS", null)).isEqualTo("deleted IS NULL");
    }

    @Test
    void condition_blocksInjectionInColumn() {
        assertThatThrownBy(() -> SqlSafe.condition("a OR 1=1 --", "=", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
