package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Arrays;
import java.util.List;

public class DistinctOperator extends LogicalOperator {
    private final List<String> distinctColumns;

    public DistinctOperator(LogicalOperator upstream, String... distinctColumns) {
        addUpstream(upstream);
        this.distinctColumns = distinctColumns != null ? Arrays.asList(distinctColumns) : List.of();
    }

    public List<String> getDistinctColumns() { return distinctColumns; }

    @Override
    public String getOperatorName() {
        return "distinct";
    }

    @Override
    public String toString() {
        return "distinct(" + String.join(", ", distinctColumns) + ")";
    }
}
