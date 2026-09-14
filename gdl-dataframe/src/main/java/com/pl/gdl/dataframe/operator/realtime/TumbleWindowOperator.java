package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class TumbleWindowOperator extends LogicalOperator {
    private final String timeColumn;
    private final String size;
    private final String unit;
    private final String offset;
    private final String offsetUnit;

    public TumbleWindowOperator(LogicalOperator upstream, String timeColumn, String size, String unit, String offset, String offsetUnit) {
        addUpstream(upstream);
        this.timeColumn = timeColumn;
        this.size = size;
        this.unit = unit;
        this.offset = offset;
        this.offsetUnit = offsetUnit;
    }

    public String getTimeColumn() { return timeColumn; }
    public String getSize() { return size; }
    public String getUnit() { return unit; }
    public String getOffset() { return offset; }
    public String getOffsetUnit() { return offsetUnit; }

    @Override
    public String getOperatorName() {
        return "tumbleWindow";
    }

    @Override
    public String toString() {
        return "tumbleWindow(" + timeColumn + ", " + size + ", " + unit + ")";
    }
}
