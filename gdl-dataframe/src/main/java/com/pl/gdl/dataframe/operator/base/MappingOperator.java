package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.LinkedHashMap;
import java.util.Map;

public class MappingOperator extends LogicalOperator {
    private final Map<String, String> mapping;

    public MappingOperator(LogicalOperator upstream, Map<String, String> mapping) {
        addUpstream(upstream);
        this.mapping = mapping != null ? new LinkedHashMap<>(mapping) : new LinkedHashMap<>();
    }

    public Map<String, String> getMapping() { return mapping; }

    @Override
    public String getOperatorName() {
        return "mapping";
    }

    @Override
    public String toString() {
        return "mapping(" + mapping + ")";
    }
}
