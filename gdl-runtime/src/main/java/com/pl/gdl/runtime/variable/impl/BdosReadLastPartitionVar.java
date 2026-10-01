package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * BDOS 最新分区变量：分区起止需要从 BDOS 元数据服务获取，当前未实现。
 */
public class BdosReadLastPartitionVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosReadLastPartitionVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "BdosReadLastPartitionVar 需要 BDOS 元数据服务提供分区起止，当前未实现");
    }
}
