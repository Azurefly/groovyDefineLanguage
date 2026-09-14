package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class WithColumnOperator extends LogicalOperator {
    private final String columnName;
    private final String expression;
    private final String type;

    public WithColumnOperator(LogicalOperator upstream, String columnName, String expression, String type) {
        addUpstream(upstream);
        this.columnName = columnName;
        this.expression = expression;
        this.type = type;
    }

    public String getColumnName() { return columnName; }
    public String getExpression() { return expression; }
    public String getType() { return type; }

    @Override
    public String getOperatorName() {
        return "withColumn";
    }

    @Override
    public String toString() {
        return "withColumn(" + columnName + ", " + expression + (type != null ? ", " + type : "") + ")";
    }
}
