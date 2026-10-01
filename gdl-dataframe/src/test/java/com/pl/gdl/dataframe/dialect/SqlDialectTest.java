package com.pl.gdl.dataframe.dialect;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 方言引用标识符与拼接函数的转义测试。
 */
public class SqlDialectTest {

    @Test
    public void testQuoteIdentifierEscapesInnerQuotes() {
        // 双引号方言：内部双引号 doubling
        assertThat(new H2SqlDialect().quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(new PostgresSqlDialect().quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(new SqliteSqlDialect().quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        // 反引号方言：内部反引号 doubling
        assertThat(new HiveSqlDialect().quoteIdentifier("a`b")).isEqualTo("`a``b`");
        assertThat(new MysqlSqlDialect().quoteIdentifier("a`b")).isEqualTo("`a``b`");
    }

    @Test
    public void testQuoteIdentifierPlainName() {
        assertThat(new H2SqlDialect().quoteIdentifier("t_user")).isEqualTo("\"t_user\"");
        assertThat(new HiveSqlDialect().quoteIdentifier("t_user")).isEqualTo("`t_user`");
    }

    @Test
    public void testQuoteIdentifierNull() {
        assertThat(new H2SqlDialect().quoteIdentifier(null)).isEqualTo("");
        assertThat(new HiveSqlDialect().quoteIdentifier(null)).isEqualTo("");
    }

    @Test
    public void testFormatConcatWsEscapesSingleQuoteDelimiter() {
        String delimiter = "o'clock";
        assertThat(new H2SqlDialect().formatConcatWs(delimiter, "name"))
                .isEqualTo("LISTAGG(name, 'o''clock')");
        assertThat(new PostgresSqlDialect().formatConcatWs(delimiter, "name"))
                .isEqualTo("string_agg(name, 'o''clock')");
        assertThat(new SqliteSqlDialect().formatConcatWs(delimiter, "name"))
                .isEqualTo("GROUP_CONCAT(name, 'o''clock')");
        assertThat(new HiveSqlDialect().formatConcatWs(delimiter, "name"))
                .isEqualTo("concat_ws('o''clock', sort_array(collect_list(name)))");
        assertThat(new MysqlSqlDialect().formatConcatWs(delimiter, "name"))
                .isEqualTo("GROUP_CONCAT(name SEPARATOR 'o''clock')");
    }

    @Test
    public void testFormatConcatWsPlainDelimiter() {
        assertThat(new H2SqlDialect().formatConcatWs(",", "name")).isEqualTo("LISTAGG(name, ',')");
        assertThat(new HiveSqlDialect().formatConcatWs(",", "name"))
                .isEqualTo("concat_ws(',', sort_array(collect_list(name)))");
    }
}
