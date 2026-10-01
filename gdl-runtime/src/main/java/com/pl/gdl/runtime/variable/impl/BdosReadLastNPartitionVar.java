package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * BDOS 最近 N 个分区变量：分区范围需要从 BDOS 元数据服务获取，当前未实现。
 */
public class BdosReadLastNPartitionVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosReadLastNPartitionVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "BdosReadLastNPartitionVar 需要 BDOS 元数据服务提供分区范围，当前未实现");
    }
}
