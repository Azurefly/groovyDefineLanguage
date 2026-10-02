package com.pl.gdl.dataframe.operator.base;

/**
 * 数据质量检查失败时抛出的异常。
 */
public class DataQualityException extends RuntimeException {
    private final long violationCount;

    public DataQualityException(String message, long violationCount) {
        super(message + " (违规行数: " + violationCount + ")");
        this.violationCount = violationCount;
    }

    public long getViolationCount() {
        return violationCount;
    }
}
