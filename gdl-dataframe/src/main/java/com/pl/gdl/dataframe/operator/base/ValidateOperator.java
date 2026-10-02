package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

/**
 * 数据质量检查算子：验证上游数据是否满足给定条件。
 * 条件不满足时抛出 DataQualityException，包含违规行数和示例。
 *
 * <p>示例：df.validate("amount > 0", "金额必须为正数")</p>
 */
public class ValidateOperator extends LogicalOperator {
    private final String condition;
    private final String message;

    public ValidateOperator(LogicalOperator upstream, String condition, String message) {
        addUpstream(upstream);
        if (condition == null || condition.isBlank()) {
            throw new IllegalArgumentException("condition must not be blank");
        }
        this.condition = condition;
        this.message = message != null ? message : "数据质量检查失败: " + condition;
    }

    public String getCondition() { return condition; }
    public String getMessage() { return message; }

    @Override
    public String getOperatorName() {
        return "validate";
    }

    @Override
    public String toString() {
        return "validate(" + condition + ")";
    }
}
