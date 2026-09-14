package com.pl.gdl.dataframe.operator.set;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Arrays;
import java.util.List;

public class SubtractOperator extends LogicalOperator {
    private final boolean all;
    private final List<String> compareKeys;

    public SubtractOperator(LogicalOperator left, LogicalOperator right, boolean all, String... compareKeys) {
        addUpstream(left);
        addUpstream(right);
        this.all = all;
        this.compareKeys = compareKeys != null ? Arrays.asList(compareKeys) : List.of();
    }

    public boolean isAll() { return all; }
    public List<String> getCompareKeys() { return compareKeys; }

    @Override
    public String getOperatorName() {
        return all ? "subtractAll" : "subtract";
    }

    @Override
    public String toString() {
        return (all ? "subtractAll(" : "subtract(") + upstream.get(1).getTempTableName() + ")";
    }
}
