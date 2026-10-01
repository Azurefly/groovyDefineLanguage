package com.pl.gdl.dataframe.operator.set;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class IntersectOperator extends LogicalOperator {
    private final boolean all;
    private final List<String> compareKeys;

    public IntersectOperator(LogicalOperator left, LogicalOperator right, boolean all, String... compareKeys) {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        addUpstream(left);
        addUpstream(right);
        this.all = all;
        this.compareKeys = compareKeys != null ? Arrays.asList(compareKeys) : List.of();
    }

    public boolean isAll() { return all; }
    public List<String> getCompareKeys() { return compareKeys; }

    @Override
    public String getOperatorName() {
        return all ? "intersectAll" : "intersect";
    }

    @Override
    public String toString() {
        return (all ? "intersectAll(" : "intersect(") + upstream.get(1).getTempTableName() + ")";
    }
}
