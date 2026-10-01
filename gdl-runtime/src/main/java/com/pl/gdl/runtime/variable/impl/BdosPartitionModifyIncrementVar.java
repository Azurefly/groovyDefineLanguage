package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * BDOS 分区修正增量变量：分区列表需要从 BDOS 元数据服务获取，当前未实现。
 */
public class BdosPartitionModifyIncrementVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosPartitionModifyIncrementVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "BdosPartitionModifyIncrementVar 需要 BDOS 元数据服务提供分区列表，当前未实现");
    }
}
