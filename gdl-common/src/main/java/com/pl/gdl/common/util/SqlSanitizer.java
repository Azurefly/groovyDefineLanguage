package com.pl.gdl.common.util;

import java.util.regex.Pattern;

/**
 * SQL 注入防护工具：标识符引用、字面量转义与标识符白名单校验。
 *
 * <p>说明：
 * <ul>
 *   <li>{@link #quoteIdentifier(String)} 使用标准 SQL 双引号包裹标识符，内部双引号按 doubling 规则转义；</li>
 *   <li>{@link #escapeLiteral(String)} 对字符串字面量中的单引号做 doubling 转义；</li>
 *   <li>{@link #isValidIdentifier(String)} 为白名单校验，仅允许 {@code ^[a-zA-Z_][a-zA-Z0-9_]*$}。</li>
 * </ul>
 * 动态拼接 SQL 时优先校验白名单，次选引用/转义；两者结合使用效果最佳。</p>
 */
public final class SqlSanitizer {
    private SqlSanitizer() {}

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * 将标识符（表名、列名）用标准 SQL 双引号包裹，其中的双引号按 doubling 规则转义为两个双引号。
     *
     * @param identifier 待引用的标识符，允许为 null
     * @return 被双引号包裹的标识符，输入为 null 时返回 null
     */
    public static String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * 对字符串字面量做单引号 doubling 转义（{@code '} → {@code ''}）。
     *
     * @param str 待转义的字符串，允许为 null
     * @return 转义后的字符串，输入为 null 时返回空字符串
     */
    public static String escapeLiteral(String str) {
        if (str == null) return "";
        return str.replace("'", "''");
    }

    /**
     * 白名单校验标识符是否安全：仅允许字母、下划线开头，后跟字母、数字或下划线。
     *
     * @param identifier 待校验的标识符，允许为 null
     * @return 符合 {@code ^[a-zA-Z_][a-zA-Z0-9_]*$} 时返回 true，否则 false
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        return IDENTIFIER_PATTERN.matcher(identifier).matches();
    }
}
