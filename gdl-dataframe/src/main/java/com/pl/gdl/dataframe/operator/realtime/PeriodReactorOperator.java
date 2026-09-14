package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class PeriodReactorOperator extends LogicalOperator {
    private final String cronExpression;

    public PeriodReactorOperator(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public PeriodReactorOperator(LogicalOperator upstream, String cronExpression) {
        addUpstream(upstream);
        this.cronExpression = cronExpression;
    }

    public String getCronExpression() { return cronExpression; }

    @Override
    public String getOperatorName() {
        return "periodReactor";
    }

    @Override
    public String toString() {
        return "periodReactor(\"" + cronExpression + "\")";
    }
}
