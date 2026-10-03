package com.pl.gdl.dataframe.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import groovy.lang.Closure;
import java.util.List;
import java.util.Map;

public interface CmdDataframe {
    // Identity & Lineage
    CmdDataframe nodeId(String nodeId);
    String getNodeId();
    CmdDataframe alias(String alias);
    String getAlias();
    CmdDataframe depend(CmdDataframe... depends);
    List<CmdDataframe> getDependencies();

    // Basic transformations
    CmdDataframe where(String condition);
    CmdDataframe select(String... expressions);
    CmdDataframe mapping(Map<String, String> mapping);
    CmdDataframe withColumn(String colName, String expression);
    CmdDataframe withColumn(String colName, String expression, String type);
    CmdDataframe group(String groupByCols, String aggregateExpressions);
    CmdDataframe sort(String... sortExpressions);
    CmdDataframe index(String rowNumCol);
    CmdDataframe distributeSort(String partitionCols, String sortCols);
    CmdDataframe limit(int limit);
    CmdDataframe limit(int offset, int limit);
    CmdDataframe distinct(String... cols);
    CmdDataframe sample(int n);
    CmdDataframe sample(double fraction);
    CmdDataframe sample(int n, long seed);
    CmdDataframe sample(double fraction, long seed);
    CmdDataframe validate(String condition, String message);
    CmdDataframe describe();
    CmdDataframe pivot(String pivotColumn, String valueColumn, String aggFunction, String... groupByColumns);
    CmdDataframe groupSortFirst(String groupCols, String sortCols);

    // Set Operations
    CmdDataframe union(CmdDataframe other);
    CmdDataframe unionAll(CmdDataframe other);
    CmdDataframe subtract(CmdDataframe other, String... keys);
    CmdDataframe subtractAll(CmdDataframe other, String... keys);
    CmdDataframe intersect(CmdDataframe other, String... keys);
    CmdDataframe intersectAll(CmdDataframe other, String... keys);

    // Joins
    CmdDataframe join(CmdDataframe other, String onCondition);
    CmdDataframe leftJoin(CmdDataframe other, String onCondition);
    CmdDataframe rightJoin(CmdDataframe other, String onCondition);
    CmdDataframe fullJoin(CmdDataframe other, String onCondition);
    CmdDataframe exists(CmdDataframe other, String onCondition);
    CmdDataframe notExists(CmdDataframe other, String onCondition);

    // Output
    CmdDataframe to(CmdDatasource ds, String tableName);
    CmdDataframe overwriteTo(CmdDatasource ds, String tableName);
    CmdDataframe fields(String ddlFields);
    CmdDataframe ttl(long seconds);
    CmdDataframe overwrite();
    CmdDataframe overwrite(String partitionSpec);
    CmdDataframe partition(String partitionExpr);
    CmdDataframe upsert();
    CmdDataframe view();

    // Realtime Windows
    // 注意：窗口算子当前仅在路线图中，调用会抛 UnsupportedOperationException
    CmdDataframe tumbleWindow(String timeCol, String size, String unit, String... offsetAndUnit);
    CmdDataframe hopWindow(String timeCol, String slide, String sUnit, String size, String wUnit, String... offsetAndUnit);
    CmdDataframe cumulateWindow(String timeCol, String step, String stepUnit, String max, String maxUnit, String... offsetAndUnit);
    /**
     * @deprecated 调度属于任务编排层，不应作为 DataFrame 算子。当前未实现，调用会抛异常。
     * 将在未来版本中移除，或迁移到 gdl-server 的任务调度器。
     */
    @Deprecated
    CmdDataframe periodReactor(String cronExpr);
    /**
     * @deprecated 增量拉取尚未实现，调用会抛异常。将在未来版本中提供独立的增量同步 API。
     */
    @Deprecated
    CmdDataframe increment(CmdDatasource ds, String querySql, String incField, int hitCount, int maxWaitSec);

    // Advanced & Script
    HttpOperator http(String method, String url);
    CmdDataframe llmCall(CmdDatasource llmDs, String model, String role, String target, String resultCol, Map<String, Object> params);
    CmdDataframe groovy(Closure<RowDataFrame> closure);

    /**
     * 立即执行并缓存当前结果。后续 collect() 直接返回缓存，不再重新计算。
     * 适用于被多次复用的中间结果。
     */
    CmdDataframe cache();

    /**
     * 清除缓存，下次 collect() 将重新计算。
     */
    CmdDataframe uncache();

    /**
     * 是否已缓存。
     */
    boolean isCached();

    // Properties & Execution
    String getTempTable();
    String getVAR_TEMP_TABLE();
    LogicalOperator getOperator();
    RowDataFrame getData();
    RowDataFrame collect();

    /**
     * 导出为 CSV 文件（UTF-8 编码，含表头，RFC 4180 转义）。
     * 这是一个终止操作（terminal operation）。
     *
     * @param filePath 目标文件路径
     */
    void writeCsv(String filePath);

    /**
     * 导出为 CSV 文件。
     *
     * @param filePath 目标文件路径
     * @param withHeader 是否含表头
     * @param delimiter 分隔符（通常为 ',' 或 '\t'）
     */
    void writeCsv(String filePath, boolean withHeader, char delimiter);

    /**
     * 导出为 JSON Lines 文件（每行一个 JSON 对象，UTF-8 编码）。
     * 这是一个终止操作（terminal operation）。
     *
     * @param filePath 目标文件路径
     */
    void writeJson(String filePath);

    /**
     * 返回从数据源到当前节点的算子变换链（数据血缘）。
     * 用于数据治理、影响分析和调试。
     *
     * @return 算子描述列表，按执行顺序排列
     */
    java.util.List<String> lineage();

    /**
     * 返回上次 collect() 的执行指标。
     * 首次调用 collect() 之前返回 null。
     *
     * @return 执行指标，或 null
     */
    ExecutionMetrics getLastMetrics();
}
