package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

import java.util.Arrays;
import java.util.List;

/**
 * 透视表算子（行转列）：将一列的唯一值转为多个列。
 *
 * <p>示例：df.pivot("quarter", "amount", "SUM", "id")
 * 将 quarter 列的 Q1/Q2/Q3/Q4 转为列，对 amount 求和，按 id 分组。</p>
 */
public class PivotOperator extends LogicalOperator {
    private final String pivotColumn;
    private final String valueColumn;
    private final String aggFunction;
    private final List<String> groupByColumns;

    public PivotOperator(LogicalOperator upstream, String pivotColumn, String valueColumn,
                         String aggFunction, String... groupByColumns) {
        addUpstream(upstream);
        if (pivotColumn == null || pivotColumn.isBlank()) {
            throw new IllegalArgumentException("pivotColumn must not be blank");
        }
        if (valueColumn == null || valueColumn.isBlank()) {
            throw new IllegalArgumentException("valueColumn must not be blank");
        }
        this.pivotColumn = pivotColumn;
        this.valueColumn = valueColumn;
        this.aggFunction = aggFunction != null && !aggFunction.isBlank()
                ? aggFunction.toUpperCase() : "SUM";
        this.groupByColumns = Arrays.asList(groupByColumns);
    }

    public String getPivotColumn() { return pivotColumn; }
    public String getValueColumn() { return valueColumn; }
    public String getAggFunction() { return aggFunction; }
    public List<String> getGroupByColumns() { return groupByColumns; }

    @Override
    public String getOperatorName() {
        return "pivot";
    }

    @Override
    public String toString() {
        return "pivot(" + pivotColumn + ", " + valueColumn + ", " + aggFunction + ")";
    }
}
