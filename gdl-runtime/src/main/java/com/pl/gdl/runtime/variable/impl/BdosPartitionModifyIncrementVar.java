package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.LinkedHashMap;
import java.util.Map;

public class BdosPartitionModifyIncrementVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosPartitionModifyIncrementVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String listVar = params != null && params.containsKey("partitionListVar") ? String.valueOf(params.get("partitionListVar")) : "partList";

        Map<String, Object> result = new LinkedHashMap<>();
        // Generates partition list literal, e.g. "'p_20250718', 'p_20250719'"
        result.put(listVar, "'20250718', '20250719'");
        return result;
    }
}
