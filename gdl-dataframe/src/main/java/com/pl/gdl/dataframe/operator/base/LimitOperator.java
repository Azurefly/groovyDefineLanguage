package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class LimitOperator extends LogicalOperator {
    private final int offset;
    private final int limit;

    public LimitOperator(LogicalOperator upstream, int limit) {
        this(upstream, 0, limit);
    }

    public LimitOperator(LogicalOperator upstream, int offset, int limit) {
        addUpstream(upstream);
        this.offset = offset;
        this.limit = limit;
    }

    public int getOffset() { return offset; }
    public int getLimit() { return limit; }

    @Override
    public String getOperatorName() {
        return "limit";
    }

    @Override
    public String toString() {
        return "limit(" + (offset > 0 ? offset + ", " : "") + limit + ")";
    }
}
