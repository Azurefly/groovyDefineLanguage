package com.pl.gdl.dataframe.engine;

import java.util.concurrent.Callable;

/**
 * Dry-run（纯规划）模式：线程级开关，激活期间所有 ExecutionEngine 不触碰
 * 数据源、不产生任何副作用（不读不写），直接返回空结果。
 *
 * <p>主要用途：{@code GdlCompiler.parseToDag} 只做 DAG 规划时开启，
 * 保证"解析"语义纯粹——脚本中的 collect/writeCsv/writeToTable/DDL 等
 * 终端动作不再实际执行。算子构造与 DAG 节点注册不受影响（仍正常构建）。
 *
 * <p>默认关闭，对正常执行零影响；支持嵌套（内层退出后恢复外层状态）。
 */
public final class DryRun {
    private static final ThreadLocal<Boolean> ACTIVE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DryRun() {
    }

    /** 当前线程是否处于 dry-run 模式。 */
    public static boolean isActive() {
        return ACTIVE.get();
    }

    /** 在 dry-run 模式下执行一段代码，退出后恢复之前的状态。 */
    public static <T> T run(Callable<T> task) throws Exception {
        boolean prev = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            return task.call();
        } finally {
            ACTIVE.set(prev);
        }
    }

    /** 在 dry-run 模式下执行一段代码（无返回值版本），退出后恢复之前的状态。 */
    public static void run(Runnable task) {
        boolean prev = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            task.run();
        } finally {
            ACTIVE.set(prev);
        }
    }
}
