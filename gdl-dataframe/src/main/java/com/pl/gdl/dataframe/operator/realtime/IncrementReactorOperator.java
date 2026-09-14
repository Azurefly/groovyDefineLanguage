package com.pl.gdl.dataframe.operator.realtime;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;

public class IncrementReactorOperator extends LogicalOperator {
    private final CmdDatasource datasource;
    private final String querySql;
    private final String incField;
    private final int hitCount;
    private final int maxWaitSec;

    public IncrementReactorOperator(LogicalOperator upstream, CmdDatasource datasource, String querySql,
                                    String incField, int hitCount, int maxWaitSec) {
        addUpstream(upstream);
        this.datasource = datasource;
        this.querySql = querySql;
        this.incField = incField;
        this.hitCount = hitCount;
        this.maxWaitSec = maxWaitSec;
    }

    public CmdDatasource getDatasource() { return datasource; }
    public String getQuerySql() { return querySql; }
    public String getIncField() { return incField; }
    public int getHitCount() { return hitCount; }
    public int getMaxWaitSec() { return maxWaitSec; }

    @Override
    public String getOperatorName() {
        return "increment";
    }

    @Override
    public String toString() {
        return "increment(" + querySql + ", " + incField + ", hit=" + hitCount + ", maxWait=" + maxWaitSec + "s)";
    }
}
