package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Arrays;
import java.util.List;

public class SortOperator extends LogicalOperator {
    private final List<String> sortExpressions;
    private String indexColumnName;

    public SortOperator(LogicalOperator upstream, String... sortExpressions) {
        addUpstream(upstream);
        this.sortExpressions = sortExpressions != null ? Arrays.asList(sortExpressions) : List.of();
    }

    public List<String> getSortExpressions() { return sortExpressions; }

    public String getIndexColumnName() { return indexColumnName; }
    public void setIndexColumnName(String indexColumnName) { this.indexColumnName = indexColumnName; }

    @Override
    public String getOperatorName() {
        return "sort";
    }

    @Override
    public String toString() {
        return "sort(" + String.join(", ", sortExpressions) + (indexColumnName != null ? ").index(" + indexColumnName : "") + ")";
    }
}
