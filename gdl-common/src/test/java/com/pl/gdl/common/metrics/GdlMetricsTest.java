package com.pl.gdl.common.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GDL 指标收集器测试。
 */
public class GdlMetricsTest {

    @BeforeEach
    void reset() {
        GdlMetrics.get().reset();
    }

    @Test
    void counter_increments() {
        GdlMetrics.get().counter("test.op");
        GdlMetrics.get().counter("test.op");
        GdlMetrics.get().counter("test.op", 5);
        Map<String, Object> snap = GdlMetrics.get().snapshot();
        assertThat(snap.get("counter.test.op")).isEqualTo(7L);
    }

    @Test
    void timer_recordsStats() {
        GdlMetrics.get().timer("test.q", 100);
        GdlMetrics.get().timer("test.q", 200);
        Map<String, Object> snap = GdlMetrics.get().snapshot();
        @SuppressWarnings("unchecked")
        Map<String, Object> timer = (Map<String, Object>) snap.get("timer.test.q");
        assertThat(timer.get("count")).isEqualTo(2L);
        assertThat(timer.get("totalMs")).isEqualTo(300L);
        assertThat(timer.get("maxMs")).isEqualTo(200L);
        assertThat(timer.get("avgMs")).isEqualTo(150L);
    }

    @Test
    void gauge_setsValue() {
        GdlMetrics.get().gauge("pool.size", 10);
        Map<String, Object> snap = GdlMetrics.get().snapshot();
        assertThat(snap.get("gauge.pool.size")).isEqualTo(10L);
    }

    @Test
    void timing_autoCloseable() {
        try (var t = GdlMetrics.get().time("test.auto")) {
            // simulate work
        }
        Map<String, Object> snap = GdlMetrics.get().snapshot();
        assertThat(snap).containsKey("timer.test.auto");
    }
}
