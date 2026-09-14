package com.pl.gdl.common.util;

public final class SqlSanitizer {
    private SqlSanitizer() {}

    public static String quoteIdentifier(String identifier) {
        if (identifier == null) return null;
        String clean = identifier.trim().replace("`", "");
        return "`" + clean + "`";
    }

    public static String escapeLiteral(String str) {
        if (str == null) return "";
        return str.replace("'", "''");
    }

    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }
}
