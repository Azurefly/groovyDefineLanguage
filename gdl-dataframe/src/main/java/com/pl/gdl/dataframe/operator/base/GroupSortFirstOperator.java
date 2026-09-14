package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class GroupSortFirstOperator extends LogicalOperator {
    private final String groupCols;
    private final String sortCols;

    public GroupSortFirstOperator(LogicalOperator upstream, String groupCols, String sortCols) {
        addUpstream(upstream);
        this.groupCols = groupCols;
        this.sortCols = sortCols;
    }

    public String getGroupCols() { return groupCols; }
    public String getSortCols() { return sortCols; }

    @Override
    public String getOperatorName() {
        return "groupSortFirst";
    }

    @Override
    public String toString() {
        return "groupSortFirst(" + groupCols + ", " + sortCols + ")";
    }
}
