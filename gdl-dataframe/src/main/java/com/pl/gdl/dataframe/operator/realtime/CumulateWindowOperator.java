package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class CumulateWindowOperator extends LogicalOperator {
    private final String timeColumn;
    private final String stepTime;
    private final String stepUnit;
    private final String windowSize;
    private final String windowUnit;
    private final String offset;
    private final String offsetUnit;

    public CumulateWindowOperator(LogicalOperator upstream, String timeColumn, String stepTime, String stepUnit,
                                  String windowSize, String windowUnit, String offset, String offsetUnit) {
        addUpstream(upstream);
        this.timeColumn = timeColumn;
        this.stepTime = stepTime;
        this.stepUnit = stepUnit;
        this.windowSize = windowSize;
        this.windowUnit = windowUnit;
        this.offset = offset;
        this.offsetUnit = offsetUnit;
    }

    public String getTimeColumn() { return timeColumn; }
    public String getStepTime() { return stepTime; }
    public String getStepUnit() { return stepUnit; }
    public String getWindowSize() { return windowSize; }
    public String getWindowUnit() { return windowUnit; }
    public String getOffset() { return offset; }
    public String getOffsetUnit() { return offsetUnit; }

    @Override
    public String getOperatorName() {
        return "cumulateWindow";
    }

    @Override
    public String toString() {
        return "cumulateWindow(" + timeColumn + ", " + stepTime + ", " + stepUnit + ", " + windowSize + ", " + windowUnit + ")";
    }
}
