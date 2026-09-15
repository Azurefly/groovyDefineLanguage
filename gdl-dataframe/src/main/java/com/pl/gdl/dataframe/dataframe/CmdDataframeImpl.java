package com.pl.gdl.dataframe.dataframe;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;
import com.pl.gdl.dataframe.engine.AutomaticFederatedJoinEngine;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.federation.OperatorDatasourceResolver;
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

public class CmdDataframeImpl implements CmdDataframe {
    private final LogicalOperator operator;
    private ExecutionEngine executionEngine;
    private RowDataFrame cachedData;

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
        operator.setAlias(alias);
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
        return new CmdDataframeImpl(new SortOperator(operator, sortExpressions), executionEngine);
    }

    @Override
    public CmdDataframe index(String rowNumCol) {
        if (operator instanceof SortOperator) {
            ((SortOperator) operator).setIndexColumnName(rowNumCol);
            return this;
        }
        SortOperator sortOp = new SortOperator(operator);
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
        return createJoin(other, JoinOperator.JoinType.INNER, onCondition);
    }

    @Override
    public CmdDataframe leftJoin(CmdDataframe other, String onCondition) {
        return createJoin(other, JoinOperator.JoinType.LEFT, onCondition);
    }

    @Override
    public CmdDataframe rightJoin(CmdDataframe other, String onCondition) {
        return createJoin(other, JoinOperator.JoinType.RIGHT, onCondition);
    }

    @Override
    public CmdDataframe fullJoin(CmdDataframe other, String onCondition) {
        return createJoin(other, JoinOperator.JoinType.FULL, onCondition);
    }

    private CmdDataframe createJoin(CmdDataframe other, JoinOperator.JoinType joinType, String onCondition) {
        Objects.requireNonNull(other, "other dataframe must not be null");
        JoinOperator join = new JoinOperator(operator, other.getOperator(), joinType, onCondition);
        ExecutionEngine selectedEngine = executionEngine;

        if (other instanceof CmdDataframeImpl otherImpl && executionEngine != null && otherImpl.executionEngine != null) {
            Optional<CmdDatasource> leftDatasource = OperatorDatasourceResolver.resolveSingle(operator);
            Optional<CmdDatasource> rightDatasource = OperatorDatasourceResolver.resolveSingle(other.getOperator());
            if (leftDatasource.isPresent() && rightDatasource.isPresent()) {
                DatasourceIdentity leftIdentity = DatasourceIdentity.from(leftDatasource.get());
                DatasourceIdentity rightIdentity = DatasourceIdentity.from(rightDatasource.get());
                if (!leftIdentity.equals(rightIdentity)) {
                    selectedEngine = new AutomaticFederatedJoinEngine(
                            executionEngine,
                            otherImpl.executionEngine,
                            leftDatasource.get(),
                            rightDatasource.get());
                }
            }
        }
        return new CmdDataframeImpl(join, selectedEngine);
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

    @Override
    public CmdDataframe tumbleWindow(String timeCol, String size, String unit, String... offsetAndUnit) {
        String offset = offsetAndUnit != null && offsetAndUnit.length > 0 ? offsetAndUnit[0] : null;
        String offsetUnit = offsetAndUnit != null && offsetAndUnit.length > 1 ? offsetAndUnit[1] : null;
        return new CmdDataframeImpl(new TumbleWindowOperator(operator, timeCol, size, unit, offset, offsetUnit), executionEngine);
    }

    @Override
    public CmdDataframe hopWindow(String timeCol, String slide, String sUnit, String size, String wUnit, String... offsetAndUnit) {
        String offset = offsetAndUnit != null && offsetAndUnit.length > 0 ? offsetAndUnit[0] : null;
        String offsetUnit = offsetAndUnit != null && offsetAndUnit.length > 1 ? offsetAndUnit[1] : null;
        return new CmdDataframeImpl(new HopWindowOperator(operator, timeCol, slide, sUnit, size, wUnit, offset, offsetUnit), executionEngine);
    }

    @Override
    public CmdDataframe cumulateWindow(String timeCol, String step, String stepUnit, String max, String maxUnit, String... offsetAndUnit) {
        String offset = offsetAndUnit != null && offsetAndUnit.length > 0 ? offsetAndUnit[0] : null;
        String offsetUnit = offsetAndUnit != null && offsetAndUnit.length > 1 ? offsetAndUnit[1] : null;
        return new CmdDataframeImpl(new CumulateWindowOperator(operator, timeCol, step, stepUnit, max, maxUnit, offset, offsetUnit), executionEngine);
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
        if (cachedData != null) {
            return cachedData;
        }
        if (executionEngine != null) {
            cachedData = executionEngine.execute(operator);
            return cachedData;
        }
        return new RowDataFrame();
    }

    @Override
    public String toString() {
        return "CmdDataframe{" + operator + "}";
    }
}
