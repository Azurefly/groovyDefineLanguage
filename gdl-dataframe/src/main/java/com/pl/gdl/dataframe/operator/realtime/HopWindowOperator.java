package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class HopWindowOperator extends LogicalOperator {
    private final String timeColumn;
    private final String slideTime;
    private final String slideUnit;
    private final String windowSize;
    private final String windowUnit;
    private final String offset;
    private final String offsetUnit;

    public HopWindowOperator(LogicalOperator upstream, String timeColumn, String slideTime, String slideUnit,
                             String windowSize, String windowUnit, String offset, String offsetUnit) {
        addUpstream(upstream);
        this.timeColumn = timeColumn;
        this.slideTime = slideTime;
        this.slideUnit = slideUnit;
        this.windowSize = windowSize;
        this.windowUnit = windowUnit;
        this.offset = offset;
        this.offsetUnit = offsetUnit;
    }

    public String getTimeColumn() { return timeColumn; }
    public String getSlideTime() { return slideTime; }
    public String getSlideUnit() { return slideUnit; }
    public String getWindowSize() { return windowSize; }
    public String getWindowUnit() { return windowUnit; }
    public String getOffset() { return offset; }
    public String getOffsetUnit() { return offsetUnit; }

    @Override
    public String getOperatorName() {
        return "hopWindow";
    }

    @Override
    public String toString() {
        return "hopWindow(" + timeColumn + ", " + slideTime + ", " + slideUnit + ", " + windowSize + ", " + windowUnit + ")";
    }
}
