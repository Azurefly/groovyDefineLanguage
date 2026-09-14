package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class GroupOperator extends LogicalOperator {
    private final String groupByCols;
    private final String aggregateExprs;

    public GroupOperator(LogicalOperator upstream, String groupByCols, String aggregateExprs) {
        addUpstream(upstream);
        this.groupByCols = groupByCols;
        this.aggregateExprs = aggregateExprs;
    }

    public String getGroupByCols() { return groupByCols; }
    public String getAggregateExprs() { return aggregateExprs; }

    @Override
    public String getOperatorName() {
        return "group";
    }

    @Override
    public String toString() {
        return "group(" + groupByCols + ", " + aggregateExprs + ")";
    }
}
