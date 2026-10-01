package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.common.util.SqlSanitizer;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * 通用增量抽取变量：根据本次执行的上下界水位生成 SQL 增量条件片段。
 *
 * <p>字段名必须通过 {@link SqlSanitizer#isValidIdentifier(String)} 校验，
 * 水位值经 {@link SqlSanitizer#escapeLiteral(String)} 转义后作为字符串字面量拼接，
 * 防止参数注入。</p>
 */
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
        if (!SqlSanitizer.isValidIdentifier(field)) {
            throw new IllegalArgumentException("非法字段名: " + field);
        }

        String min = params != null && params.containsKey("execMinPoint") ? String.valueOf(params.get("execMinPoint")) : null;
        String max = params != null && params.containsKey("execMaxPoint") ? String.valueOf(params.get("execMaxPoint")) : null;

        if (min != null && max != null) {
            return new IncrementCondition(field + " >= '" + SqlSanitizer.escapeLiteral(min)
                    + "' AND " + field + " <= '" + SqlSanitizer.escapeLiteral(max) + "'");
        } else if (max != null) {
            return new IncrementCondition(field + " <= '" + SqlSanitizer.escapeLiteral(max) + "'");
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
