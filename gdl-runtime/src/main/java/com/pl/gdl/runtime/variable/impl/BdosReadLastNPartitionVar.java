package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.LinkedHashMap;
import java.util.Map;

public class BdosReadLastNPartitionVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosReadLastNPartitionVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String beginVar = params != null && params.containsKey("partitionBeginVar") ? String.valueOf(params.get("partitionBeginVar")) : "pBegin";
        String endVar = params != null && params.containsKey("partitionEndVar") ? String.valueOf(params.get("partitionEndVar")) : "pEnd";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(beginVar, "'20250701'");
        result.put(endVar, "'20250719'");
        return result;
    }
}
