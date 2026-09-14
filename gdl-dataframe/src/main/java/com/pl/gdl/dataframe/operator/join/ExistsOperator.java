package com.pl.gdl.dataframe.operator.join;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class ExistsOperator extends LogicalOperator {
    private final boolean not;
    private final String onCondition;

    public ExistsOperator(LogicalOperator left, LogicalOperator right, boolean not, String onCondition) {
        addUpstream(left);
        addUpstream(right);
        this.not = not;
        this.onCondition = onCondition;
    }

    public boolean isNot() { return not; }
    public String getOnCondition() { return onCondition; }

    @Override
    public String getOperatorName() {
        return not ? "notExists" : "exists";
    }

    @Override
    public String toString() {
        return (not ? "notExists(" : "exists(") + upstream.get(1).getTempTableName() + ", " + onCondition + ")";
    }
}
