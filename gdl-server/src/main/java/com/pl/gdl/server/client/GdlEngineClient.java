package com.pl.gdl.server.client;

import com.pl.gdl.common.model.OntoInfoRsp;
import com.pl.gdl.common.model.RegisterRsp;
import com.pl.gdl.common.model.TaskResult;

import java.util.List;
import java.util.Map;

/**
 * GDL 引擎远程服务客户端抽象：本体注册/查询、GDL 任务提交/查询、GDL 脚本转 DAG。
 *
 * <p>两种实现：</p>
 * <ul>
 *   <li>{@link GdlEngineClientImpl}——本地进程内实现，直接调用各引擎模块；</li>
 *   <li>{@link GdlHttpEngineClient}——远程 HTTP 实现，调用远端服务的
 *       {@code /gdl/api/*} 接口。</li>
 * </ul>
 *
 * <p>任务提交均为异步语义：{@link #startTask(String, Map)} 立即返回 taskId，
 * 再用 {@link #getTaskResult(String)} 轮询执行结果。</p>
 */
public interface GdlEngineClient {
    /**
     * 注册单个本体。
     *
     * @param gdl 本体定义的 GDL 脚本
     * @return 注册结果
     */
    RegisterRsp registerOntology(String gdl);

    /**
     * 批量注册本体；遇到失败即中断并返回失败结果。
     *
     * @param gdls 本体定义的 GDL 脚本列表
     * @return 最后一次注册的结果（全部成功时为成功结果）
     */
    RegisterRsp registerOntologies(List<String> gdls);

    /**
     * 批量更新本体（语义等同于重新注册）。
     *
     * @param gdls 本体定义的 GDL 脚本列表
     * @return 最后一次注册的结果
     */
    RegisterRsp updateOntologies(List<String> gdls);

    /**
     * 注销本体。
     *
     * @param names 本体全限定名（多个以逗号分隔，具体格式见实现）
     * @return 注销结果
     */
    RegisterRsp unregisterOntology(String names);

    /**
     * 查询本体元信息（默认区域 {@code "local"}）。
     *
     * @param fullOntologyNames 本体全限定名，{@code null} 或空表示查询全部
     * @return 本体元信息列表
     */
    List<OntoInfoRsp> getOntologies(String fullOntologyNames);

    /**
     * 查询指定区域的本体元信息。
     *
     * @param fullOntologyNames 本体全限定名，{@code null} 或空表示查询全部
     * @param areaCode          区域编码
     * @return 本体元信息列表
     */
    List<OntoInfoRsp> getOntologies(String fullOntologyNames, String areaCode);

    /**
     * 异步提交 GDL 脚本任务。
     *
     * <p>调用后立即返回 taskId，不等待执行完成；随后用
     * {@link #getTaskResult(String)} 轮询结果。</p>
     *
     * @param gdlScript 待执行的 GDL 脚本
     * @param params    脚本参数，可为 {@code null}
     * @return 任务 ID
     */
    String startTask(String gdlScript, Map<String, Object> params);

    /**
     * 查询任务执行结果。
     *
     * @param taskId 任务 ID
     * @return 任务结果；任务不存在时返回 {@code null}
     */
    TaskResult getTaskResult(String taskId);

    /**
     * 将 GDL 脚本解析为 DAG 并返回其 JSON 表示。
     * <p>将 GML（GDL 模型语言）脚本解析为 DAG。</p>
     *
     * @param gdlScript GDL 脚本
     * @return DAG 的 JSON 字符串
     */
    String getGmlToDag(String gdlScript);
}
