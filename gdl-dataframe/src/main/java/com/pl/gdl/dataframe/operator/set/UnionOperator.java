package com.pl.gdl.dataframe.operator.set;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class UnionOperator extends LogicalOperator {
    private final boolean all;

    public UnionOperator(LogicalOperator left, LogicalOperator right, boolean all) {
        addUpstream(left);
        addUpstream(right);
        this.all = all;
    }

    public boolean isAll() { return all; }

    @Override
    public String getOperatorName() {
        return all ? "unionAll" : "union";
    }

    @Override
    public String toString() {
        return (all ? "unionAll(" : "union(") + upstream.get(1).getTempTableName() + ")";
    }
}
