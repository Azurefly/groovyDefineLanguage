package com.pl.gdl.dataframe.dataframe;

/**
 * DataFrame 执行指标：用于可观测性和排障。
 */
public class ExecutionMetrics {
    private final long elapsedMillis;
    private final long rowCount;
    private final String sql;
    private final boolean fromCache;

    public ExecutionMetrics(long elapsedMillis, long rowCount, String sql, boolean fromCache) {
        this.elapsedMillis = elapsedMillis;
        this.rowCount = rowCount;
        this.sql = sql;
        this.fromCache = fromCache;
    }

    /** 执行耗时（毫秒） */
    public long getElapsedMillis() { return elapsedMillis; }

    /** 返回行数 */
    public long getRowCount() { return rowCount; }

    /** 生成的 SQL（非 SQL 引擎返回 null） */
    public String getSql() { return sql; }

    /** 是否命中缓存 */
    public boolean isFromCache() { return fromCache; }

    @Override
    public String toString() {
        return "ExecutionMetrics{elapsed=" + elapsedMillis + "ms, rows=" + rowCount +
                ", fromCache=" + fromCache + (sql != null ? ", sql=" + sql : "") + "}";
    }
}
