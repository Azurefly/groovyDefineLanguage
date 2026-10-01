package com.pl.gdl.common.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 表示数据结果集中的一行。
 *
 * <p>字段名大小写不敏感：内部统一按小写存储与查找，例如 {@code "ID"}、
 * {@code "id"}、{@code "Id"} 均指向同一字段。大小写归一化使用
 * {@link Locale#ROOT}，避免土耳其语等区域设置下 {@code I}/{@code i}
 * 转换不一致的问题。</p>
 */
public class Row implements Serializable {
    private final Map<String, Object> values;

    public Row() {
        this.values = new LinkedHashMap<>();
    }

    public Row(Map<String, Object> values) {
        this.values = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((k, v) -> this.values.put(k != null ? k.toLowerCase(Locale.ROOT) : null, v));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String fieldName) {
        if (fieldName == null) return null;
        Object val = values.get(fieldName.toLowerCase(Locale.ROOT));
        return (T) val;
    }

    public void setValue(String fieldName, Object value) {
        if (fieldName != null) {
            values.put(fieldName.toLowerCase(Locale.ROOT), value);
        }
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public boolean containsKey(String fieldName) {
        return fieldName != null && values.containsKey(fieldName.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
