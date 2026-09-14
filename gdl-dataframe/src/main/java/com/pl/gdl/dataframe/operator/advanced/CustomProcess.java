package com.pl.gdl.dataframe.operator.advanced;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public abstract class CustomProcess extends LogicalOperator {
    public CustomProcess() {
        super();
    }

    public CustomProcess(LogicalOperator upstream) {
        super();
        addUpstream(upstream);
    }

    public abstract RowDataFrame process(RowDataFrame df);

    @Override
    public String getOperatorName() {
        return getClass().getSimpleName();
    }
}
