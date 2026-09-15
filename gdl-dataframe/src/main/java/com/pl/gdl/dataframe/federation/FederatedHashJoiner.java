package com.pl.gdl.dataframe.federation;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.join.JoinOperator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Hash join over two materialized provider results. */
public final class FederatedHashJoiner {
    private FederatedHashJoiner() {}

    public static RowDataFrame join(RowDataFrame left,
                                    RowDataFrame right,
                                    String leftKey,
                                    String rightKey,
                                    String leftAlias,
                                    String rightAlias,
                                    JoinOperator.JoinType joinType) {
        List<Map<String, Object>> leftRows = left == null ? List.of() : left.toListMap();
        List<Map<String, Object>> rightRows = right == null ? List.of() : right.toListMap();
        Set<String> leftColumns = columns(left, leftRows);
        Set<String> rightColumns = columns(right, rightRows);
        String effectiveLeftAlias = normalizeAlias(leftAlias, "left");
        String effectiveRightAlias = normalizeAlias(rightAlias, "right");
        JoinOperator.JoinType effectiveType = joinType == null ? JoinOperator.JoinType.INNER : joinType;

        Map<Object, List<IndexedRow>> rightIndex = new LinkedHashMap<>();
        for (int index = 0; index < rightRows.size(); index++) {
            Map<String, Object> row = rightRows.get(index);
            Object key = hashKey(value(row, rightKey));
            rightIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new IndexedRow(index, row));
        }

        boolean[] matchedRight = new boolean[rightRows.size()];
        RowDataFrame result = new RowDataFrame(outputColumns(leftColumns, rightColumns, effectiveLeftAlias, effectiveRightAlias));

        for (Map<String, Object> leftRow : leftRows) {
            Object key = hashKey(value(leftRow, leftKey));
            List<IndexedRow> matches = rightIndex.getOrDefault(key, Collections.emptyList());
            if (matches.isEmpty()) {
                if (effectiveType == JoinOperator.JoinType.LEFT || effectiveType == JoinOperator.JoinType.FULL) {
                    result.addRowValue(merge(leftRow, null, leftColumns, rightColumns, effectiveLeftAlias, effectiveRightAlias));
                }
                continue;
            }
            for (IndexedRow match : matches) {
                matchedRight[match.index()] = true;
                result.addRowValue(merge(leftRow, match.row(), leftColumns, rightColumns, effectiveLeftAlias, effectiveRightAlias));
            }
        }

        if (effectiveType == JoinOperator.JoinType.RIGHT || effectiveType == JoinOperator.JoinType.FULL) {
            for (int index = 0; index < rightRows.size(); index++) {
                if (!matchedRight[index]) {
                    result.addRowValue(merge(null, rightRows.get(index), leftColumns, rightColumns, effectiveLeftAlias, effectiveRightAlias));
                }
            }
        }
        return result;
    }

    private static List<ColumnInfo> outputColumns(Set<String> leftColumns,
                                                   Set<String> rightColumns,
                                                   String leftAlias,
                                                   String rightAlias) {
        List<ColumnInfo> columns = new ArrayList<>();
        leftColumns.forEach(name -> columns.add(new ColumnInfo(leftAlias + "." + name, "string")));
        rightColumns.forEach(name -> columns.add(new ColumnInfo(rightAlias + "." + name, "string")));
        return columns;
    }

    private static Map<String, Object> merge(Map<String, Object> left,
                                             Map<String, Object> right,
                                             Set<String> leftColumns,
                                             Set<String> rightColumns,
                                             String leftAlias,
                                             String rightAlias) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String column : leftColumns) {
            merged.put(leftAlias + "." + column, left == null ? null : value(left, column));
        }
        for (String column : rightColumns) {
            merged.put(rightAlias + "." + column, right == null ? null : value(right, column));
        }
        return merged;
    }

    private static Set<String> columns(RowDataFrame frame, List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        if (frame != null && frame.getColumns() != null) {
            frame.getColumns().forEach(column -> columns.add(column.getColumnName()));
        }
        rows.forEach(row -> columns.addAll(row.keySet()));
        return columns;
    }

    private static Object value(Map<String, Object> row, String requested) {
        if (row == null) return null;
        if (row.containsKey(requested)) return row.get(requested);
        String normalized = requested.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) return entry.getValue();
        }
        throw new IllegalArgumentException("Column '" + requested + "' not found. Available: " + row.keySet());
    }

    private static Object hashKey(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        return value;
    }

    private static String normalizeAlias(String alias, String fallback) {
        return alias == null || alias.isBlank() ? fallback : alias.trim();
    }

    private record IndexedRow(int index, Map<String, Object> row) {}
}
