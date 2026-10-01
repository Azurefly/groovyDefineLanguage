package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * BDOS 最新单个分区变量：分区信息需要从 BDOS 元数据服务获取，当前未实现。
 */
public class BdosReadLastOnePartitionVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "BdosReadLastOnePartitionVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        throw new UnsupportedOperationException(
                "BdosReadLastOnePartitionVar 需要 BDOS 元数据服务提供最新分区，当前未实现");
    }
}
