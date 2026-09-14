package com.pl.gdl.dataframe.operator.join;

import com.pl.gdl.dataframe.operator.LogicalOperator;

public class JoinOperator extends LogicalOperator {
    public enum JoinType {
        INNER("join"),
        LEFT("leftJoin"),
        RIGHT("rightJoin"),
        FULL("fullJoin");

        private final String syntaxName;

        JoinType(String syntaxName) {
            this.syntaxName = syntaxName;
        }

        public String getSyntaxName() { return syntaxName; }
    }

    private final JoinType joinType;
    private final String onCondition;

    public JoinOperator(LogicalOperator left, LogicalOperator right, JoinType joinType, String onCondition) {
        addUpstream(left);
        addUpstream(right);
        this.joinType = joinType != null ? joinType : JoinType.INNER;
        this.onCondition = onCondition;
    }

    public JoinType getJoinType() { return joinType; }
    public String getOnCondition() { return onCondition; }

    @Override
    public String getOperatorName() {
        return joinType.getSyntaxName();
    }

    @Override
    public String toString() {
        return joinType.getSyntaxName() + "(" + upstream.get(1).getTempTableName() + ", " + onCondition + ")";
    }
}
