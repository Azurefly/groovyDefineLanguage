package com.pl.gdl.dataframe.operator.join;

import com.pl.gdl.dataframe.operator.LogicalOperator;
import java.util.Objects;

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
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        addUpstream(left);
        addUpstream(right);
        this.joinType = joinType != null ? joinType : JoinType.INNER;
        this.onCondition = onCondition;
    }

    public JoinType getJoinType() { return joinType; }
    public String getOnCondition() { return onCondition; }

    @Override
    public String getOperatorName() {
        // 构造期间超类 LogicalOperator 会触发监听器回调 getOperatorName()，
        // 此时 joinType 尚未赋值（final 字段在 super() 返回后才初始化），必须判空。
        return joinType == null ? "join" : joinType.getSyntaxName();
    }

    @Override
    public String toString() {
        if (joinType == null) return "join(<constructing>)";
        return joinType.getSyntaxName() + "(" + upstream.get(1).getTempTableName() + ", " + onCondition + ")";
    }
}
