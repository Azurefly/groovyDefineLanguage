package com.pl.gdl.dataframe.federation;

import java.util.Locale;

/** A deliberately narrow parser for automatic cross-source equi-join rewrite. */
public record EquiJoinCondition(String leftKey, String rightKey) {

    public static EquiJoinCondition parse(String condition) {
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("Federated join condition must not be blank");
        }
        String normalized = condition.toUpperCase(Locale.ROOT);
        if (normalized.contains(" AND ") || normalized.contains(" OR ")) {
            throw new UnsupportedOperationException(
                    "Automatic federated join currently supports one equality condition, got: " + condition);
        }
        String[] parts = condition.split("=", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new UnsupportedOperationException(
                    "Automatic federated join currently supports one equality condition, got: " + condition);
        }
        return new EquiJoinCondition(column(parts[0]), column(parts[1]));
    }

    private static String column(String expression) {
        String token = expression.trim();
        if (token.contains(" ") || token.contains("(") || token.contains(")") ||
                token.contains("<") || token.contains(">") || token.contains("!")) {
            throw new UnsupportedOperationException(
                    "Automatic federated join only supports direct column equality, got: " + expression.trim());
        }
        int dot = token.lastIndexOf('.');
        if (dot >= 0) token = token.substring(dot + 1);
        token = token.replace("`", "").replace("\"", "").trim();
        if (!token.matches("[A-Za-z_][A-Za-z0-9_$]*")) {
            throw new UnsupportedOperationException("Unsupported federated join column: " + token);
        }
        return token;
    }
}
