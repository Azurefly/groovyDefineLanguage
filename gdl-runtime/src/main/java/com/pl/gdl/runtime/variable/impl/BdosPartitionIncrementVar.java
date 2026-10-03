package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * @deprecated 分区增量抽取变量：按分区水位计算增量抽取范围。当前未实现，调用会抛 UnsupportedOperationException。
 * 如需类似功能，请自行实现 DynamicVariable 接口。
 */
@Deprecated
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
