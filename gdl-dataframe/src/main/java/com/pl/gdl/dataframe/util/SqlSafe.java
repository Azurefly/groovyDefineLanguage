package com.pl.gdl.dataframe.util;

import java.util.Set;

/**
 * SQL 注入防护工具。
 *
 * <p>GDL 的 {@code where(String)} / {@code query(ds, sql)} 接受原始 SQL 片段，
 * 若条件中混入外部输入（如 HTTP 参数），必须经本工具处理：
 * <ul>
 *   <li>列名/表名用 {@link #identifier(String)} 校验（白名单字符集）</li>
 *   <li>值用 {@link #literal(Object)} 转义，或优先使用 {@code where(col, op, value)} 参数化 API</li>
 * </ul>
 */
public final class SqlSafe {
    private SqlSafe() {}

    /** 允许的操作符白名单 */
    private static final Set<String> OPERATORS = Set.of(
            "=", "!=", "<>", ">", "<", ">=", "<=", "LIKE", "NOT LIKE",
            "IN", "NOT IN", "IS", "IS NOT", "BETWEEN");

    /** SQL 关键字黑名单（标识符校验用） */
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "UNION", "WHERE",
            "OR", "AND", "FROM", "JOIN", "EXEC", "EXECUTE", "DECLARE", "TABLE");

    /**
     * 校验标识符（列名/表名）合法性：仅允许字母数字下划线加点号，且不能是 SQL 关键字。
     * 非法时抛 IllegalArgumentException。
     */
    public static String identifier(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("标识符不能为空");
        String n = name.trim();
        if (!n.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
            throw new IllegalArgumentException("非法标识符（疑似 SQL 注入）: " + name);
        }
        String upper = n.toUpperCase();
        for (String part : upper.split("\\.")) {
            if (KEYWORDS.contains(part)) throw new IllegalArgumentException("标识符不能是 SQL 关键字: " + name);
        }
        return n;
    }

    /** 校验操作符是否在白名单 */
    public static String operator(String op) {
        if (op == null) throw new IllegalArgumentException("操作符不能为空");
        String u = op.trim().toUpperCase();
        if (!OPERATORS.contains(u)) throw new IllegalArgumentException("不支持的操作符（疑似 SQL 注入）: " + op);
        return u;
    }

    /**
     * 将 Java 值转义为 SQL 字面量。
     * null -> NULL；数字/布尔直接输出；字符串单引号转义；日期转字符串。
     */
    public static String literal(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof java.util.Date d) {
            return "'" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d) + "'";
        }
        String s = String.valueOf(value).replace("'", "''");
        return "'" + s + "'";
    }

    /**
     * 构造参数化条件：{@code col OP value}，列名与操作符经白名单校验，值经转义。
     * 例：condition("age", ">", 18) -> "age > 18"
     */
    public static String condition(String column, String op, Object value) {
        String col = identifier(column);
        String oper = operator(op);
        if ("IN".equals(oper) || "NOT IN".equals(oper)) {
            if (!(value instanceof Iterable<?> iter))
                throw new IllegalArgumentException("IN 操作符的值必须是集合");
            StringBuilder sb = new StringBuilder();
            for (Object v : iter) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(literal(v));
            }
            if (sb.length() == 0) throw new IllegalArgumentException("IN 集合不能为空");
            return col + " " + oper + " (" + sb + ")";
        }
        if ("IS".equals(oper) || "IS NOT".equals(oper)) {
            if (value != null) throw new IllegalArgumentException("IS/IS NOT 只能与 NULL 连用");
            return col + " " + oper + " NULL";
        }
        return col + " " + oper + " " + literal(value);
    }
}
