package com.pl.gdl.dataframe.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.advanced.HttpOperator;
import com.pl.gdl.dataframe.operator.advanced.LlmCallOperator;
import com.pl.gdl.dataframe.operator.advanced.GroovyCustomOperator;
import com.pl.gdl.dataframe.operator.base.*;
import com.pl.gdl.dataframe.operator.join.ExistsOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator;
import com.pl.gdl.dataframe.operator.output.ToOperator;
import com.pl.gdl.dataframe.operator.realtime.*;
import com.pl.gdl.dataframe.operator.set.IntersectOperator;
import com.pl.gdl.dataframe.operator.set.SubtractOperator;
import com.pl.gdl.dataframe.operator.set.UnionOperator;
import groovy.lang.Closure;

import java.util.*;

/**
 * {@link CmdDataframe} 的默认实现：包装一个 {@link LogicalOperator} 算子链节点，并持有执行引擎。
 * <p>
 * 链式调用的返回语义分为两类，调用时需注意：
 * <ul>
 *   <li><b>返回新实例</b>：当前节点保持不变，返回包装了新算子的全新实例。
 *       包括 {@code alias / where / select / mapping / withColumn / group / sort /
 *       distributeSort / limit / distinct / groupSortFirst / union / unionAll /
 *       subtract / subtractAll / intersect / intersectAll / join / leftJoin /
 *       rightJoin / fullJoin / exists / notExists / to / overwriteTo /
 *       tumbleWindow / hopWindow / cumulateWindow / periodReactor / increment /
 *       llmCall / groovy}，
 *       以及当前算子不是 {@code SortOperator} 时的 {@code index}。</li>
 *   <li><b>原地修改并返回 this</b>：直接改动当前节点持有的算子，返回同一实例。
 *       包括 {@code nodeId / depend / fields / ttl / overwrite() /
 *       overwrite(partitionSpec) / partition / upsert / view}，
 *       以及当前算子已经是 {@code SortOperator} 时的 {@code index}。</li>
 * </ul>
 */
public class CmdDataframeImpl implements CmdDataframe {
    private final LogicalOperator operator;
    private ExecutionEngine executionEngine;
    private RowDataFrame cachedData;
    private ExecutionMetrics lastMetrics;

    public CmdDataframeImpl(LogicalOperator operator) {
        this.operator = operator;
    }

    public CmdDataframeImpl(LogicalOperator operator, ExecutionEngine executionEngine) {
        this.operator = operator;
        this.executionEngine = executionEngine;
    }

    public void setExecutionEngine(ExecutionEngine executionEngine) {
        this.executionEngine = executionEngine;
        this.cachedData = null;
    }

    public ExecutionEngine getExecutionEngine() {
        return executionEngine;
    }

    @Override
    public CmdDataframe nodeId(String nodeId) {
        operator.setNodeId(nodeId);
        return this;
    }

    @Override
    public String getNodeId() {
        return operator.getNodeId();
    }

    @Override
    public CmdDataframe alias(String alias) {
        // 仅包装 AliasOperator，不再同步 mutation 底层算子：此前双重应用会导致
        // (SELECT * FROM t AS a) a 这类别名嵌套，H2 解析外层 a.tool_code 时混乱
        return new CmdDataframeImpl(new AliasOperator(operator, alias), executionEngine);
    }

    @Override
    public String getAlias() {
        return operator.getAlias();
    }

    @Override
    public CmdDataframe depend(CmdDataframe... depends) {
        if (depends != null) {
            for (CmdDataframe d : depends) {
                if (d != null) {
                    operator.addDependency(d.getOperator());
                }
            }
        }
        return this;
    }

    @Override
    public List<CmdDataframe> getDependencies() {
        List<CmdDataframe> list = new ArrayList<>();
        for (LogicalOperator dep : operator.getDependencies()) {
            list.add(new CmdDataframeImpl(dep, executionEngine));
        }
        return list;
    }

    @Override
    public CmdDataframe where(String condition) {
        return new CmdDataframeImpl(new WhereOperator(operator, condition), executionEngine);
    }

    @Override
    public CmdDataframe where(String column, String operator, Object value) {
        return where(com.pl.gdl.dataframe.util.SqlSafe.condition(column, operator, value));
    }

    @Override
    public CmdDataframe where(java.util.Map<String, Object> equals) {
        if (equals == null || equals.isEmpty()) throw new IllegalArgumentException("条件 Map 不能为空");
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, Object> e : equals.entrySet()) {
            Object v = e.getValue();
            parts.add(v == null
                    ? com.pl.gdl.dataframe.util.SqlSafe.condition(e.getKey(), "IS", null)
                    : com.pl.gdl.dataframe.util.SqlSafe.condition(e.getKey(), "=", v));
        }
        return where(String.join(" AND ", parts));
    }

    @Override
    public CmdDataframe select(String... expressions) {
        return new CmdDataframeImpl(new SelectOperator(operator, expressions), executionEngine);
    }

    @Override
    public CmdDataframe mapping(Map<String, String> mapping) {
        return new CmdDataframeImpl(new MappingOperator(operator, mapping), executionEngine);
    }

    @Override
    public CmdDataframe withColumn(String colName, String expression) {
        return withColumn(colName, expression, null);
    }

    @Override
    public CmdDataframe withColumn(String colName, String expression, String type) {
        return new CmdDataframeImpl(new WithColumnOperator(operator, colName, expression, type), executionEngine);
    }

    @Override
    public CmdDataframe group(String groupByCols, String aggregateExpressions) {
        return new CmdDataframeImpl(new GroupOperator(operator, groupByCols, aggregateExpressions), executionEngine);
    }

    @Override
    public CmdDataframe sort(String... sortExpressions) {
        if (sortExpressions == null || sortExpressions.length == 0) {
            throw new IllegalArgumentException("sortExpressions must not be empty, use index() for row numbering without sorting");
        }
        return new CmdDataframeImpl(new SortOperator(operator, sortExpressions), executionEngine);
    }

    @Override
    public CmdDataframe index(String rowNumCol) {
        // 保持不可变性：始终创建新的 SortOperator，不修改已有算子
        SortOperator sortOp;
        if (operator instanceof SortOperator) {
            // 复制已有排序表达式到新算子
            SortOperator existing = (SortOperator) operator;
            sortOp = new SortOperator(existing.getUpstream().get(0),
                    existing.getSortExpressions().toArray(new String[0]));
        } else {
            sortOp = new SortOperator(operator);
        }
        sortOp.setIndexColumnName(rowNumCol);
        return new CmdDataframeImpl(sortOp, executionEngine);
    }

    @Override
    public CmdDataframe distributeSort(String partitionCols, String sortCols) {
        return new CmdDataframeImpl(new DistributeSortOperator(operator, partitionCols, sortCols), executionEngine);
    }

    @Override
    public CmdDataframe limit(int limit) {
        return limit(0, limit);
    }

    @Override
    public CmdDataframe limit(int offset, int limit) {
        return new CmdDataframeImpl(new LimitOperator(operator, offset, limit), executionEngine);
    }

    @Override
    public CmdDataframe distinct(String... cols) {
        return new CmdDataframeImpl(new DistinctOperator(operator, cols), executionEngine);
    }

    @Override
    public CmdDataframe sample(int n) {
        return new CmdDataframeImpl(new SampleOperator(operator, n), executionEngine);
    }

    @Override
    public CmdDataframe sample(double fraction) {
        return new CmdDataframeImpl(new SampleOperator(operator, fraction), executionEngine);
    }

    @Override
    public CmdDataframe sample(int n, long seed) {
        return new CmdDataframeImpl(new SampleOperator(operator, n, seed), executionEngine);
    }

    @Override
    public CmdDataframe sample(double fraction, long seed) {
        return new CmdDataframeImpl(new SampleOperator(operator, fraction, seed), executionEngine);
    }

    @Override
    public CmdDataframe validate(String condition, String message) {
        return new CmdDataframeImpl(new ValidateOperator(operator, condition, message), executionEngine);
    }

    @Override
    public CmdDataframe describe() {
        return new CmdDataframeImpl(new DescribeOperator(operator), executionEngine);
    }

    @Override
    public CmdDataframe pivot(String pivotColumn, String valueColumn, String aggFunction, String... groupByColumns) {
        return new CmdDataframeImpl(new PivotOperator(operator, pivotColumn, valueColumn, aggFunction, groupByColumns), executionEngine);
    }

    @Override
    public CmdDataframe groupSortFirst(String groupCols, String sortCols) {
        return new CmdDataframeImpl(new GroupSortFirstOperator(operator, groupCols, sortCols), executionEngine);
    }

    @Override
    public CmdDataframe union(CmdDataframe other) {
        return new CmdDataframeImpl(new UnionOperator(operator, other.getOperator(), false), executionEngine);
    }

    @Override
    public CmdDataframe unionAll(CmdDataframe other) {
        return new CmdDataframeImpl(new UnionOperator(operator, other.getOperator(), true), executionEngine);
    }

    @Override
    public CmdDataframe subtract(CmdDataframe other, String... keys) {
        return new CmdDataframeImpl(new SubtractOperator(operator, other.getOperator(), false, keys), executionEngine);
    }

    @Override
    public CmdDataframe subtractAll(CmdDataframe other, String... keys) {
        return new CmdDataframeImpl(new SubtractOperator(operator, other.getOperator(), true, keys), executionEngine);
    }

    @Override
    public CmdDataframe intersect(CmdDataframe other, String... keys) {
        return new CmdDataframeImpl(new IntersectOperator(operator, other.getOperator(), false, keys), executionEngine);
    }

    @Override
    public CmdDataframe intersectAll(CmdDataframe other, String... keys) {
        return new CmdDataframeImpl(new IntersectOperator(operator, other.getOperator(), true, keys), executionEngine);
    }

    @Override
    public CmdDataframe join(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new JoinOperator(operator, other.getOperator(), JoinOperator.JoinType.INNER, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe leftJoin(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new JoinOperator(operator, other.getOperator(), JoinOperator.JoinType.LEFT, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe rightJoin(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new JoinOperator(operator, other.getOperator(), JoinOperator.JoinType.RIGHT, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe fullJoin(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new JoinOperator(operator, other.getOperator(), JoinOperator.JoinType.FULL, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe exists(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new ExistsOperator(operator, other.getOperator(), false, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe notExists(CmdDataframe other, String onCondition) {
        return new CmdDataframeImpl(new ExistsOperator(operator, other.getOperator(), true, onCondition), executionEngine);
    }

    @Override
    public CmdDataframe to(CmdDatasource ds, String tableName) {
        return new CmdDataframeImpl(new ToOperator(operator, ds, tableName), executionEngine);
    }

    @Override
    public CmdDataframe overwriteTo(CmdDatasource ds, String tableName) {
        ToOperator toOp = new ToOperator(operator, ds, tableName);
        toOp.setOverwrite(true);
        return new CmdDataframeImpl(toOp, executionEngine);
    }

    @Override
    public CmdDataframe fields(String ddlFields) {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setFieldsDdl(ddlFields);
        }
        return this;
    }

    @Override
    public CmdDataframe ttl(long seconds) {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setTtlSeconds(seconds);
        }
        return this;
    }

    @Override
    public CmdDataframe overwrite() {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setOverwrite(true);
        }
        return this;
    }

    @Override
    public CmdDataframe overwrite(String partitionSpec) {
        if (operator instanceof ToOperator) {
            ToOperator toOp = (ToOperator) operator;
            toOp.setOverwritePartition(true);
            toOp.setPartitionSpec(partitionSpec);
        }
        return this;
    }

    @Override
    public CmdDataframe partition(String partitionExpr) {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setPartitionSpec(partitionExpr);
        }
        return this;
    }

    @Override
    public CmdDataframe upsert() {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setUpsert(true);
        }
        return this;
    }

    @Override
    public CmdDataframe view() {
        if (operator instanceof ToOperator) {
            ((ToOperator) operator).setView(true);
        }
        return this;
    }

    /**
     * 解析窗口算子的可选 offset 参数（DU-03 去重）。
     * offsetAndUnit 为可变参数：长度 0 → 无 offset；长度 1 → offset 值；长度 2 → offset 值 + 单位。
     *
     * @return 二元组 [offset, offsetUnit]，缺失时对应位置为 null
     */
    private static String[] parseWindowOffset(String... offsetAndUnit) {
        String offset = offsetAndUnit != null && offsetAndUnit.length > 0 ? offsetAndUnit[0] : null;
        String offsetUnit = offsetAndUnit != null && offsetAndUnit.length > 1 ? offsetAndUnit[1] : null;
        return new String[]{offset, offsetUnit};
    }

    @Override
    public CmdDataframe tumbleWindow(String timeCol, String size, String unit, String... offsetAndUnit) {
        String[] offset = parseWindowOffset(offsetAndUnit);
        return new CmdDataframeImpl(new TumbleWindowOperator(operator, timeCol, size, unit, offset[0], offset[1]), executionEngine);
    }

    @Override
    public CmdDataframe hopWindow(String timeCol, String slide, String sUnit, String size, String wUnit, String... offsetAndUnit) {
        String[] offset = parseWindowOffset(offsetAndUnit);
        return new CmdDataframeImpl(new HopWindowOperator(operator, timeCol, slide, sUnit, size, wUnit, offset[0], offset[1]), executionEngine);
    }

    @Override
    public CmdDataframe cumulateWindow(String timeCol, String step, String stepUnit, String max, String maxUnit, String... offsetAndUnit) {
        String[] offset = parseWindowOffset(offsetAndUnit);
        return new CmdDataframeImpl(new CumulateWindowOperator(operator, timeCol, step, stepUnit, max, maxUnit, offset[0], offset[1]), executionEngine);
    }

    @Override
    public CmdDataframe periodReactor(String cronExpr) {
        return new CmdDataframeImpl(new PeriodReactorOperator(operator, cronExpr), executionEngine);
    }

    @Override
    public CmdDataframe increment(CmdDatasource ds, String querySql, String incField, int hitCount, int maxWaitSec) {
        return new CmdDataframeImpl(new IncrementReactorOperator(operator, ds, querySql, incField, hitCount, maxWaitSec), executionEngine);
    }

    @Override
    public HttpOperator http(String method, String url) {
        return new HttpOperator(operator, method, url);
    }

    @Override
    public CmdDataframe llmCall(CmdDatasource llmDs, String model, String role, String target, String resultCol, Map<String, Object> params) {
        if (!(llmDs instanceof com.pl.gdl.dataframe.datasource.LlmDatasource)) {
            throw new IllegalArgumentException("llmCall 需要 LlmDatasource，传入的是 "
                    + (llmDs == null ? "null" : llmDs.getClass().getSimpleName()));
        }
        return new CmdDataframeImpl(new LlmCallOperator(operator, llmDs, model, role, target, resultCol, params), executionEngine);
    }

    @Override
    public CmdDataframe groovy(Closure<RowDataFrame> closure) {
        return new CmdDataframeImpl(new GroovyCustomOperator(operator, closure), executionEngine);
    }

    @Override
    public String getTempTable() {
        return operator.getTempTableName();
    }

    @Override
    public String getVAR_TEMP_TABLE() {
        return operator.getTempTableName();
    }

    @Override
    public LogicalOperator getOperator() {
        return operator;
    }

    @Override
    public RowDataFrame getData() {
        return collect();
    }

    @Override
    public RowDataFrame collect() {
        long start = System.currentTimeMillis();
        boolean fromCache = false;
        String sql = null;
        RowDataFrame result;
        if (cachedData != null) {
            result = cachedData;
            fromCache = true;
        } else if (executionEngine != null) {
            // 尝试获取生成的 SQL（用于排障）
            if (executionEngine instanceof com.pl.gdl.dataframe.engine.InMemoryEngine) {
                try {
                    sql = ((com.pl.gdl.dataframe.engine.InMemoryEngine) executionEngine).toSql(operator);
                } catch (Exception ignored) {
                    // 非 SQL 算子（如 validate/describe/pivot）无 SQL，忽略
                }
            }
            cachedData = executionEngine.execute(operator);
            result = cachedData;
        } else {
            result = new RowDataFrame();
        }
        long elapsed = System.currentTimeMillis() - start;
        lastMetrics = new ExecutionMetrics(elapsed, result.rowSize(), sql, fromCache);
        return result;
    }

    @Override
    public ExecutionMetrics getLastMetrics() {
        return lastMetrics;
    }

    @Override
    public String toString() {
        return "CmdDataframe{" + operator + "}";
    }

    @Override
    public CmdDataframe cache() {
        collect();
        return this;
    }

    @Override
    public CmdDataframe uncache() {
        cachedData = null;
        return this;
    }

    @Override
    public boolean isCached() {
        return cachedData != null;
    }

    @Override
    public void writeCsv(String filePath) {
        writeCsv(filePath, true, ',');
    }

    @Override
    public void writeCsv(String filePath, boolean withHeader, char delimiter) {
        // dry-run（纯规划）模式：不落地文件，避免副作用
        if (com.pl.gdl.dataframe.engine.DryRun.isActive()) return;
        RowDataFrame data = collect();
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                java.nio.file.Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8)) {
            if (withHeader) {
                writer.write(data.getColumns().stream()
                        .map(c -> escapeCsv(c.getColumnName(), delimiter))
                        .collect(java.util.stream.Collectors.joining(String.valueOf(delimiter))));
                writer.newLine();
            }
            for (com.pl.gdl.common.model.Row row : data) {
                String line = data.getColumns().stream()
                        .map(c -> escapeCsv(toCsvString(row.getValue(c.getColumnName())), delimiter))
                        .collect(java.util.stream.Collectors.joining(String.valueOf(delimiter)));
                writer.write(line);
                writer.newLine();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("写入 CSV 失败: " + filePath + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void writeJson(String filePath) {
        // dry-run（纯规划）模式：不落地文件，避免副作用
        if (com.pl.gdl.dataframe.engine.DryRun.isActive()) return;
        RowDataFrame data = collect();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(
                java.nio.file.Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8)) {
            for (com.pl.gdl.common.model.Row row : data) {
                java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                for (com.pl.gdl.common.model.ColumnInfo col : data.getColumns()) {
                    map.put(col.getColumnName(), row.getValue(col.getColumnName()));
                }
                writer.write(mapper.writeValueAsString(map));
                writer.newLine();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("写入 JSON 失败: " + filePath + ", " + e.getMessage(), e);
        }
    }

    /**
     * CSV 字段转义（RFC 4180）：含分隔符、引号、换行时用双引号包裹，内部引号 doubling。
     */
    private static String escapeCsv(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(delimiter) >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (needQuote) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String toCsvString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    public java.util.List<String> lineage() {
        java.util.List<String> chain = new java.util.ArrayList<>();
        collectLineage(operator, chain);
        java.util.Collections.reverse(chain);
        return chain;
    }

    private static void collectLineage(com.pl.gdl.dataframe.operator.LogicalOperator op,
                                       java.util.List<String> chain) {
        if (op == null) {
            return;
        }
        chain.add(op.toString());
        // 只追踪第一条上游链（线性血缘）；多上游（如 join/union）追踪所有分支
        for (com.pl.gdl.dataframe.operator.LogicalOperator upstream : op.getUpstream()) {
            collectLineage(upstream, chain);
        }
    }
}
