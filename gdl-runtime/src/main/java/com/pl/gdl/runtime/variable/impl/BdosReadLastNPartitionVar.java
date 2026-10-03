package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * @deprecated 读取最新 N 个分区、计算增量抽取范围的变量。当前未实现，调用会抛 UnsupportedOperationException。
 * 如需类似功能，请自行实现 DynamicVariable 接口。
 */
@Deprecated
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
