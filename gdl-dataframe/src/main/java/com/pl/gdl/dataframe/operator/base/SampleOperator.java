package com.pl.gdl.dataframe.operator.base;

import com.pl.gdl.dataframe.operator.LogicalOperator;

/**
 * 数据采样算子：从上游数据中随机抽取样本。
 * 支持按行数采样（sample(n)）或按比例采样（sample(0.1)）。
 */
public class SampleOperator extends LogicalOperator {
    private final Integer sampleSize;
    private final Double fraction;
    private final Long seed;

    public SampleOperator(LogicalOperator upstream, int sampleSize) {
        this(upstream, sampleSize, null);
    }

    public SampleOperator(LogicalOperator upstream, int sampleSize, Long seed) {
        addUpstream(upstream);
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be > 0, but was " + sampleSize);
        }
        this.sampleSize = sampleSize;
        this.fraction = null;
        this.seed = seed;
    }

    public SampleOperator(LogicalOperator upstream, double fraction) {
        this(upstream, fraction, null);
    }

    public SampleOperator(LogicalOperator upstream, double fraction, Long seed) {
        addUpstream(upstream);
        if (fraction <= 0 || fraction > 1) {
            throw new IllegalArgumentException("fraction must be in (0, 1], but was " + fraction);
        }
        this.sampleSize = null;
        this.fraction = fraction;
        this.seed = seed;
    }

    public Integer getSampleSize() { return sampleSize; }
    public Double getFraction() { return fraction; }
    public Long getSeed() { return seed; }
    public boolean isBySize() { return sampleSize != null; }

    @Override
    public String getOperatorName() {
        return "sample";
    }

    @Override
    public String toString() {
        return "sample(" + (isBySize() ? sampleSize : fraction) + (seed != null ? ", seed=" + seed : "") + ")";
    }
}
