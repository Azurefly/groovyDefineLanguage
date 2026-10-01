package com.pl.gdl.common.model;

import groovy.lang.Closure;
import java.io.Serializable;
import java.util.*;
import java.util.function.Consumer;

/**
 * 基于 {@link Row} 的内存数据框：携带列定义（{@link ColumnInfo}）与行数据。
 *
 * <p>{@link #addRowValue(List)} 要求值列表与列定义一一对应：
 * 传入 null 列表或值个数与列数不一致时抛出 {@link IllegalArgumentException}，
 * 不会静默截断或缺列。</p>
 */
public class RowDataFrame implements Iterable<Row>, Serializable {
    private List<ColumnInfo> columns;
    private final List<Row> rows;

    public RowDataFrame() {
        this.columns = new ArrayList<>();
        this.rows = new ArrayList<>();
    }

    public RowDataFrame(List<ColumnInfo> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
        this.rows = new ArrayList<>();
    }

    public RowDataFrame(List<ColumnInfo> columns, List<Row> rows) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
        this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
    }

    public void addRow(Row row) {
        if (row != null) {
            rows.add(row);
        }
    }

    /**
     * 按列顺序追加一行数据。
     *
     * @param values 与列定义一一对应的值列表，个数必须等于列数
     * @throws IllegalArgumentException 当 values 为 null，或值个数与列数不一致时
     */
    public void addRowValue(List<?> values) {
        if (values == null) {
            throw new IllegalArgumentException(
                    "Row values must not be null, expected column count: " + columns.size());
        }
        if (values.size() != columns.size()) {
            throw new IllegalArgumentException(
                    "Row values count mismatch: actual=" + values.size()
                            + ", expected column count=" + columns.size());
        }
        Row row = new Row();
        for (int i = 0; i < values.size(); i++) {
            row.setValue(columns.get(i).getColumnName(), values.get(i));
        }
        rows.add(row);
    }

    public void addRowValue(Map<String, Object> values) {
        rows.add(new Row(values));
    }

    public Row getRow(int index) {
        return rows.get(index);
    }

    public int rowSize() {
        return rows.size();
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public List<ColumnInfo> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : new ArrayList<>();
    }

    public List<Row> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public List<Map<String, Object>> toListMap() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(new LinkedHashMap<>(row.getValues()));
        }
        return list;
    }

    @Override
    public Iterator<Row> iterator() {
        return getRows().iterator();
    }

    @Override
    public void forEach(Consumer<? super Row> action) {
        rows.forEach(action);
    }

    public void forEach(Closure<?> closure) {
        for (Row row : rows) {
            closure.call(row);
        }
    }

    @Override
    public String toString() {
        return "RowDataFrame{columns=" + columns.size() + ", rows=" + rows.size() + "}";
    }
}
