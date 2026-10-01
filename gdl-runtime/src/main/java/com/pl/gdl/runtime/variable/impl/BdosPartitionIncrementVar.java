package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * BDOS 分区增量变量：分区起止水位需要从 BDOS 元数据服务获取，当前未实现。
 */
public class BdosPartitionIncrementVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosPartitionIncrementVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "BdosPartitionIncrementVar 需要 BDOS 元数据服务提供分区水位，当前未实现");
    }
}
