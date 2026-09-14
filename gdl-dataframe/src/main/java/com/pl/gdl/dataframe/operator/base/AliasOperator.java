package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class AliasOperator extends LogicalOperator {
    private final String aliasName;

    public AliasOperator(LogicalOperator upstream, String aliasName) {
        addUpstream(upstream);
        this.aliasName = aliasName;
        this.alias = aliasName;
    }

    public String getAliasName() { return aliasName; }

    @Override
    public String getOperatorName() {
        return "alias";
    }

    @Override
    public String toString() {
        return "alias(" + aliasName + ")";
    }
}
