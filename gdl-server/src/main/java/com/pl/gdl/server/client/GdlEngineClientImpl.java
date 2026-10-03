package com.pl.gdl.server.client;

import com.pl.gdl.common.enums.TaskStatus;
import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;
import com.pl.gdl.drift.orchestrator.FederatedExecutionCoordinator;
import com.pl.gdl.ontology.registry.OntologyRegistry;
import com.pl.gdl.runtime.compiler.GdlCompiler;
import com.pl.gdl.runtime.dag.DagGraph;
import com.pl.gdl.runtime.dag.DagSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * {@link GdlEngineClient} 的本地（进程内）实现，直接调用各引擎模块完成任务。
 *
 * <p>任务提交为<b>真异步</b>：{@link #startTask(String, Map)} 生成 taskId 后先写入
 * {@code RUNNING} 状态占位并立即返回，实际的联邦执行在内部守护线程池中进行；
 * 执行完成后把 {@code FINISHED} 结果写回，发生异常则写回 {@code EXCEPTION} 结果。</p>
 *
 * <p>任务结果缓存带 30 分钟 TTL（读取时惰性检查过期）与 1000 条上限：
 * 写入超限时先清理已过期条目，仍超限则淘汰创建时间最早的条目。</p>
 */
public class GdlEngineClientImpl implements GdlEngineClient {
    private static final Logger log = LoggerFactory.getLogger(GdlEngineClientImpl.class);

    /** 任务结果缓存 TTL：30 分钟。 */
    private static final long TASK_RESULT_TTL_MILLIS = 30L * 60 * 1000;
    /** 任务结果缓存条数上限。 */
    private static final int TASK_RESULT_MAX_SIZE = 1000;

    private final OntologyRegistry ontologyRegistry = OntologyRegistry.getInstance();
    private final FederatedExecutionCoordinator coordinator = new FederatedExecutionCoordinator();
    private final GdlCompiler compiler = new GdlCompiler();
    private final Map<String, TimestampedResult> taskResults = new ConcurrentHashMap<>();

    /** 任务后台执行线程池（daemon 线程，不阻塞 JVM 退出）。 */
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "gdl-task-executor");
                t.setDaemon(true);
                return t;
            });

    /** 携带写入时间的任务结果包装，用于 TTL 过期与最早条目淘汰。 */
    private static final class TimestampedResult {
        final TaskResult result;
        final long createdAt;

        TimestampedResult(TaskResult result, long createdAt) {
            this.result = result;
            this.createdAt = createdAt;
        }

        boolean isExpired(long now) {
            return now - createdAt >= TASK_RESULT_TTL_MILLIS;
        }
    }

    @Override
    public RegisterRsp registerOntology(String gdl) {
        return ontologyRegistry.registerOntology(gdl);
    }

    @Override
    public RegisterRsp registerOntologies(List<String> gdls) {
        return ontologyRegistry.registerOntologies(gdls);
    }

    @Override
    public RegisterRsp updateOntologies(List<String> gdls) {
        return ontologyRegistry.updateOntologies(gdls);
    }

    @Override
    public RegisterRsp unregisterOntology(String names) {
        return ontologyRegistry.unregisterOntology(names);
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames) {
        return getOntologies(fullOntologyNames, "local");
    }

    @Override
    public List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode) {
        return ontologyRegistry.getOntologies(fullOntologyNames, areaCode);
    }

    @Override
    public String startTask(String gdlScript, Map<String, Object> params) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        // 先写入 RUNNING 状态占位，保证提交后可立即查询到任务
        storeResult(taskId, new TaskResult(taskId, TaskStatus.RUNNING));

        Map<String, Object> safeParams = params != null ? new HashMap<>(params) : new HashMap<>();
        taskExecutor.submit(() -> {
            try {
                TaskResult result = coordinator.executeFederated(gdlScript, "local", safeParams);
                result.setTaskId(taskId);
                storeResult(taskId, result);
            } catch (Exception e) {
                log.warn("Task {} execution failed", taskId, e);
                TaskResult failed = new TaskResult(taskId, TaskStatus.EXCEPTION);
                failed.setMessage(e.getMessage());
                storeResult(taskId, failed);
            }
        });
        return taskId;
    }

    @Override
    public TaskResult getTaskResult(String taskId) {
        if (taskId == null) return null;
        TimestampedResult entry = taskResults.get(taskId);
        if (entry == null) return null;
        // 惰性检查过期：已过期则移除并视为不存在
        if (entry.isExpired(System.currentTimeMillis())) {
            taskResults.remove(taskId, entry);
            return null;
        }
        return entry.result;
    }

    @Override
    public String getTsmlToDag(String gdlScript) {
        DagGraph dag = compiler.parseToDag(gdlScript, Map.of());
        return DagSerializer.toJson(dag);
    }

    /**
     * 关闭任务后台线程池。
     *
     * <p>会立即中断正在执行的任务并等待线程池终止；通常由宿主（如 {@code GdlHttpServer#stop}）
     * 在服务停止时调用。daemon 线程本身不会阻塞 JVM 退出，此方法主要用于确定性地释放资源。</p>
     */
    public void close() {
        taskExecutor.shutdownNow();
        try {
            if (!taskExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Task executor did not terminate within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for task executor termination");
        }
    }

    /** 写入任务结果，并执行 TTL/上限淘汰。 */
    private void storeResult(String taskId, TaskResult result) {
        long now = System.currentTimeMillis();
        if (taskResults.size() >= TASK_RESULT_MAX_SIZE) {
            evictIfNecessary(now);
        }
        taskResults.put(taskId, new TimestampedResult(result, now));
    }

    /**
     * 缓存淘汰：先清理已过期条目；若仍超限，则淘汰创建时间最早的条目，
     * 直到条数低于上限。
     */
    private void evictIfNecessary(long now) {
        taskResults.entrySet().removeIf(e -> e.getValue().isExpired(now));
        while (taskResults.size() >= TASK_RESULT_MAX_SIZE) {
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, TimestampedResult> e : taskResults.entrySet()) {
                if (e.getValue().createdAt < oldestTime) {
                    oldestTime = e.getValue().createdAt;
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey == null) break;
            taskResults.remove(oldestKey);
        }
    }
}
