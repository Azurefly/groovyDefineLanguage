package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Arrays;
import java.util.List;

public class SelectOperator extends LogicalOperator {
    private final List<String> expressions;

    public SelectOperator(LogicalOperator upstream, String... expressions) {
        addUpstream(upstream);
        this.expressions = expressions != null ? Arrays.asList(expressions) : List.of();
    }

    public List<String> getExpressions() { return expressions; }

    @Override
    public String getOperatorName() {
        return "select";
    }

    @Override
    public String toString() {
        return "select(" + String.join(", ", expressions) + ")";
    }
}
