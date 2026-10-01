package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class WhereOperator extends LogicalOperator {
    private final String condition;

    public WhereOperator(LogicalOperator upstream, String condition) {
        addUpstream(upstream);
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("condition must not be blank");
        }
        this.condition = condition;
    }

    public String getCondition() { return condition; }

    @Override
    public String getOperatorName() {
        return "where";
    }

    @Override
    public String toString() {
        return "where(" + condition + ")";
    }
}
