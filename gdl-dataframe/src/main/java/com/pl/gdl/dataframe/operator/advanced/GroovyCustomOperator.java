package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import groovy.lang.Closure;

public class GroovyCustomOperator extends LogicalOperator {
    private final Closure<RowDataFrame> closure;

    public GroovyCustomOperator(Closure<RowDataFrame> closure) {
        this.closure = closure;
    }

    public GroovyCustomOperator(LogicalOperator upstream, Closure<RowDataFrame> closure) {
        addUpstream(upstream);
        this.closure = closure;
    }

    public Closure<RowDataFrame> getClosure() { return closure; }

    @Override
    public String getOperatorName() {
        return "groovy";
    }

    @Override
    public String toString() {
        return "groovy(closure)";
    }
}
