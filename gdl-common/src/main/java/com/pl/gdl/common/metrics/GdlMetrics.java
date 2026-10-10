package com.pl.gdl.common.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * GDL 内置指标收集器（零依赖、线程安全）。
 *
 * <p>记录三类指标：
 * <ul>
 *   <li>counter：累计计数（如执行次数）</li>
 *   <li>timer：耗时分布（次数、总耗时、最大耗时）</li>
 *   <li>gauge：瞬时值（如连接池大小）</li>
 * </ul>
 *
 * <p>通过 {@link #snapshot()} 导出为 Map，可对接 Prometheus / 日志 / JMX。
 * 慢查询阈值：{@code gdl.metrics.slowQueryMs}（默认 1000ms）。
 */
public final class GdlMetrics {
    private static final GdlMetrics INSTANCE = new GdlMetrics();

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    /** 慢查询阈值（毫秒），系统属性 gdl.metrics.slowQueryMs，默认 1000 */
    public static long slowQueryThresholdMs() {
        return Long.getLong("gdl.metrics.slowQueryMs", 1000L);
    }

    /** 是否启用指标，系统属性 gdl.metrics.enabled，默认 true */
    public static boolean enabled() {
        return Boolean.parseBoolean(System.getProperty("gdl.metrics.enabled", "true"));
    }

    public static GdlMetrics get() { return INSTANCE; }

    private GdlMetrics() {}

    public void counter(String name) {
        if (!enabled()) return;
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    public void counter(String name, long delta) {
        if (!enabled()) return;
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    /** 记录一次耗时（毫秒），返回耗时供链式调用 */
    public long timer(String name, long millis) {
        if (!enabled()) return millis;
        timers.computeIfAbsent(name, k -> new Timer()).record(millis);
        return millis;
    }

    public void gauge(String name, long value) {
        if (!enabled()) return;
        gauges.computeIfAbsent(name, k -> new AtomicLong()).set(value);
    }

    /** 计时器辅助：try (var t = GdlMetrics.get().time("sql.query")) { ... } */
    public Timing time(String name) {
        return new Timing(name);
    }

    public class Timing implements AutoCloseable {
        private final String name;
        private final long start = System.currentTimeMillis();
        Timing(String name) { this.name = name; }
        public long elapsed() { return System.currentTimeMillis() - start; }
        @Override public void close() { timer(name, elapsed()); }
    }

    /** 导出快照：name -> {count, totalMs, maxMs} / 计数 / 瞬时值 */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        counters.forEach((k, v) -> out.put("counter." + k, v.sum()));
        timers.forEach((k, t) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("count", t.count.sum());
            m.put("totalMs", t.total.sum());
            m.put("maxMs", t.max.get());
            m.put("avgMs", t.count.sum() == 0 ? 0 : t.total.sum() / t.count.sum());
            out.put("timer." + k, m);
        });
        gauges.forEach((k, v) -> out.put("gauge." + k, v.get()));
        return out;
    }

    /** 重置所有指标（测试用） */
    public void reset() {
        counters.clear(); timers.clear(); gauges.clear();
    }

    private static class Timer {
        final LongAdder count = new LongAdder();
        final LongAdder total = new LongAdder();
        final AtomicLong max = new AtomicLong();
        void record(long ms) {
            count.increment(); total.add(ms);
            max.updateAndGet(cur -> Math.max(cur, ms));
        }
    }
}
