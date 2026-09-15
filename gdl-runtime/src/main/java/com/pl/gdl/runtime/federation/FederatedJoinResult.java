package com.pl.gdl.runtime.federation;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.runtime.plan.ExecutionPlan;

import java.util.List;

public record FederatedJoinResult(
        RowDataFrame rows,
        List<ExecutionPlan> sourcePlans,
        List<ExchangeTrace> exchanges
) {
    public record ExchangeTrace(
            String sourceAlias,
            ExecutionDomain sourceDomain,
            int rowCount,
            ExchangeBoundary.MaterializationMode mode
    ) {}
}
