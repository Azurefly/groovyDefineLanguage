package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class DistributeSortOperator extends LogicalOperator {
    private final String partitionCols;
    private final String sortCols;

    public DistributeSortOperator(LogicalOperator upstream, String partitionCols, String sortCols) {
        addUpstream(upstream);
        this.partitionCols = partitionCols;
        this.sortCols = sortCols;
    }

    public String getPartitionCols() { return partitionCols; }
    public String getSortCols() { return sortCols; }

    @Override
    public String getOperatorName() {
        return "distributeSort";
    }

    @Override
    public String toString() {
        return "distributeSort(" + partitionCols + ", " + sortCols + ")";
    }
}
