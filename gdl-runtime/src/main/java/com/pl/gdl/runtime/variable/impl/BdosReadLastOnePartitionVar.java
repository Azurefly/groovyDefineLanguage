package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * @deprecated 读取最新一个分区的变量。当前未实现，调用会抛 UnsupportedOperationException。
 * 如需类似功能，请自行实现 DynamicVariable 接口。
 */
@Deprecated
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
