package com.pl.gdl.common.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Row implements Serializable {
    private final Map<String, Object> values;

    public Row() {
        this.values = new LinkedHashMap<>();
    }

    public Row(Map<String, Object> values) {
        this.values = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((k, v) -> this.values.put(k != null ? k.toLowerCase() : null, v));
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String fieldName) {
        if (fieldName == null) return null;
        Object val = values.get(fieldName.toLowerCase());
        return (T) val;
    }

    public void setValue(String fieldName, Object value) {
        if (fieldName != null) {
            values.put(fieldName.toLowerCase(), value);
        }
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public boolean containsKey(String fieldName) {
        return fieldName != null && values.containsKey(fieldName.toLowerCase());
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
