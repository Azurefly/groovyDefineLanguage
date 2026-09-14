package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.LinkedHashMap;
import java.util.Map;

public class BdosPartitionIncrementVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosPartitionIncrementVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String beginVar = params != null && params.containsKey("partitionBeginVar") ? String.valueOf(params.get("partitionBeginVar")) : "pBegin";
        String endVar = params != null && params.containsKey("partitionEndVar") ? String.valueOf(params.get("partitionEndVar")) : "pEnd";

        String min = params != null && params.containsKey("execMinPoint") ? String.valueOf(params.get("execMinPoint")) : "0";
        String max = params != null && params.containsKey("execMaxPoint") ? String.valueOf(params.get("execMaxPoint")) : "100";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(beginVar, min);
        result.put(endVar, max);
        return result;
    }
}
