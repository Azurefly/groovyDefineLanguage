package com.pl.gdl.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlSanitizerTest {

    @Test
    public void testQuoteIdentifierUsesDoubleQuotes() {
        assertThat(SqlSanitizer.quoteIdentifier("users")).isEqualTo("\"users\"");
        assertThat(SqlSanitizer.quoteIdentifier("order")).isEqualTo("\"order\"");
    }

    @Test
    public void testQuoteIdentifierEscapesEmbeddedQuotesByDoubling() {
        assertThat(SqlSanitizer.quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(SqlSanitizer.quoteIdentifier("\"x\"")).isEqualTo("\"\"\"x\"\"\"");
    }

    @Test
    public void testQuoteIdentifierEmptyAndNull() {
        assertThat(SqlSanitizer.quoteIdentifier("")).isEqualTo("\"\"");
        assertThat(SqlSanitizer.quoteIdentifier(null)).isNull();
    }

    @Test
    public void testEscapeLiteral() {
        assertThat(SqlSanitizer.escapeLiteral("O'Brien")).isEqualTo("O''Brien");
        assertThat(SqlSanitizer.escapeLiteral("no quotes")).isEqualTo("no quotes");
        assertThat(SqlSanitizer.escapeLiteral("")).isEqualTo("");
        assertThat(SqlSanitizer.escapeLiteral(null)).isEqualTo("");
    }

    @Test
    public void testIsValidIdentifier() {
        assertThat(SqlSanitizer.isValidIdentifier("users")).isTrue();
        assertThat(SqlSanitizer.isValidIdentifier("_tmp1")).isTrue();
        assertThat(SqlSanitizer.isValidIdentifier("A1_b2")).isTrue();

        assertThat(SqlSanitizer.isValidIdentifier(null)).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("   ")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("1abc")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("a-b")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("a b")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("a;b")).isFalse();
        assertThat(SqlSanitizer.isValidIdentifier("a\"b")).isFalse();
    }
}
