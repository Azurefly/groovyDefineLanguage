package com.pl.gdl.runtime.plan;

import com.pl.gdl.dataframe.engine.ExecutionEngine;

public record ExecutionPlan(
        Mode mode,
        ExecutionIntent intent,
        String datasourceType,
        ExecutionEngine engine,
        String reason
) {
    public enum Mode {
        PROVIDER_PUSHDOWN,
        FALLBACK
    }
}
