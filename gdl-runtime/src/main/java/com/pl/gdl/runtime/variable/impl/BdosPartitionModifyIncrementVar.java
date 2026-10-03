package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.util.Map;

/**
 * @deprecated 按分区最后修改时间增量抽取、生成分区列表的变量。当前未实现，调用会抛 UnsupportedOperationException。
 * 如需类似功能，请自行实现 DynamicVariable 接口。
 */
@Deprecated
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
