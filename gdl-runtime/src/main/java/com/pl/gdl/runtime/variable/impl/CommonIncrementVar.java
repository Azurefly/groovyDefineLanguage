package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

public class CommonIncrementVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "CommonIncrementVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String field = params != null && params.containsKey("fieldAliasName")
                ? String.valueOf(params.get("fieldAliasName"))
                : (params != null && params.containsKey("queryFieldName") ? String.valueOf(params.get("queryFieldName")) : "create_time");

        String min = params != null && params.containsKey("execMinPoint") ? String.valueOf(params.get("execMinPoint")) : null;
        String max = params != null && params.containsKey("execMaxPoint") ? String.valueOf(params.get("execMaxPoint")) : null;

        if (min != null && max != null) {
            return new IncrementCondition(field + " >= '" + min + "' AND " + field + " <= '" + max + "'");
        } else if (max != null) {
            return new IncrementCondition(field + " <= '" + max + "'");
        } else {
            // Default initial extraction fragment
            return new IncrementCondition(field + " <= NOW()");
        }
    }

    public static class IncrementCondition {
        private final String sqlCondition;

        public IncrementCondition(String sqlCondition) {
            this.sqlCondition = sqlCondition;
        }

        @Override
        public String toString() {
            return sqlCondition;
        }
    }
}
