package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public interface ExecutionEngine {
    /**
     * 执行算子链并返回结果数据。
     */
    RowDataFrame execute(LogicalOperator operator);

    /**
     * 将算子链翻译为对应方言的可执行 SQL（不执行）。
     */
    String toSql(LogicalOperator operator);
}
