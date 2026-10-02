package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

/**
 * 数据探查算子：对上游数据的每列生成统计信息。
 * 输出列：column_name, data_type, row_count, null_count, distinct_count,
 * min_value, max_value, avg_value（数值列才有意义）。
 */
public class DescribeOperator extends LogicalOperator {

    public DescribeOperator(LogicalOperator upstream) {
        addUpstream(upstream);
    }

    @Override
    public String getOperatorName() {
        return "describe";
    }

    @Override
    public String toString() {
        return "describe()";
    }
}
