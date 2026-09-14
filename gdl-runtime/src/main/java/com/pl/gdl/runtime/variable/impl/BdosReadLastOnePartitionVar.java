package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.LinkedHashMap;
import java.util.Map;

public class BdosReadLastOnePartitionVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosReadLastOnePartitionVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String varName = params != null && params.containsKey("partitionVar") ? String.valueOf(params.get("partitionVar")) : "lastPartition";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(varName, "'20250719'");
        return result;
    }
}
