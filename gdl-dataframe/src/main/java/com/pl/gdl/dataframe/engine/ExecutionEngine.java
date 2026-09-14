package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public interface ExecutionEngine {
    RowDataFrame execute(LogicalOperator operator);
    String toSql(LogicalOperator operator);
}
