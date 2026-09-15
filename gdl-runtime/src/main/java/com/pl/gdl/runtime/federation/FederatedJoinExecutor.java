package com.pl.gdl.runtime.federation;

import com.pl.gdl.common.model.ColumnInfo;
import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.runtime.plan.DatasourceExecutionPlanner;
import com.pl.gdl.runtime.plan.ExecutionIntent;
import com.pl.gdl.runtime.plan.ExecutionPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * First executable federation path: push source queries to independent
 * providers, materialize their results through an in-memory exchange, then
 * perform a deterministic hash join. Cross-area exchange remains delegated to
 * Drift and is rejected here rather than silently pretending to be local.
 */
public class FederatedJoinExecutor {
    private final DatasourceRegistry registry;
    private final DatasourceExecutionPlanner planner;
    private final ExecutionEngine fallbackEngine;

    public FederatedJoinExecutor() {
        this(DatasourceRegistry.getDefault(), new DatasourceExecutionPlanner(), new InMemoryEngine());
    }

    public FederatedJoinExecutor(DatasourceRegistry registry,
                                 DatasourceExecutionPlanner planner,
                                 ExecutionEngine fallbackEngine) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.fallbackEngine = Objects.requireNonNull(fallbackEngine, "fallbackEngine must not be null");
    }

    public FederatedJoinResult execute(FederatedJoinRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ExecutionDomain leftDomain = domain(request.leftDatasource());
        ExecutionDomain rightDomain = domain(request.rightDatasource());
        if (!leftDomain.areaCode().equals(rightDomain.areaCode())) {
            throw new UnsupportedOperationException("Cross-area federation requires REMOTE_DRIFT exchange: "
                    + leftDomain.areaCode() + " -> " + rightDomain.areaCode());
        }

        ExecutionPlan leftPlan = sourcePlan(request.leftDatasource());
        ExecutionPlan rightPlan = sourcePlan(request.rightDatasource());
        RowDataFrame left = executeSource(leftPlan, request.leftDatasource(), request.leftSql());
        RowDataFrame right = executeSource(rightPlan, request.rightDatasource(), request.rightSql());

        RowDataFrame joined = hashJoin(left, right, request);
        List<FederatedJoinResult.ExchangeTrace> exchanges = List.of(
                new FederatedJoinResult.ExchangeTrace(request.leftAlias(), leftDomain, left.rowSize(), ExchangeBoundary.MaterializationMode.MEMORY),
                new FederatedJoinResult.ExchangeTrace(request.rightAlias(), rightDomain, right.rowSize(), ExchangeBoundary.MaterializationMode.MEMORY));
        return new FederatedJoinResult(joined, List.of(leftPlan, rightPlan), exchanges);
    }

    private ExecutionPlan sourcePlan(CmdDatasource datasource) {
        ExecutionPlan plan = planner.plan(datasource, ExecutionIntent.SQL_READ, registry, fallbackEngine);
        if (plan.mode() != ExecutionPlan.Mode.PROVIDER_PUSHDOWN) {
            throw new UnsupportedOperationException("Federated source requires provider pushdown for "
                    + datasource.getDatasourceType() + ": " + plan.reason());
        }
        return plan;
    }

    private static RowDataFrame executeSource(ExecutionPlan plan, CmdDatasource datasource, String sql) {
        return plan.engine().execute(new QueryOperator(datasource, sql));
    }

    private static RowDataFrame hashJoin(RowDataFrame left, RowDataFrame right, FederatedJoinRequest request) {
        List<Map<String, Object>> leftRows = left.toListMap();
        List<Map<String, Object>> rightRows = right.toListMap();
        Set<String> leftColumns = collectColumns(leftRows);
        Set<String> rightColumns = collectColumns(rightRows);

        Map<Object, List<Map<String, Object>>> rightIndex = new LinkedHashMap<>();
        for (Map<String, Object> row : rightRows) {
            Object key = value(row, request.rightKey());
            rightIndex.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }

        RowDataFrame result = new RowDataFrame();
        List<ColumnInfo> columns = new ArrayList<>();
        leftColumns.forEach(name -> columns.add(new ColumnInfo(request.leftAlias() + "." + name, "string")));
        rightColumns.forEach(name -> columns.add(new ColumnInfo(request.rightAlias() + "." + name, "string")));
        result.setColumns(columns);

        for (Map<String, Object> leftRow : leftRows) {
            Object leftKey = value(leftRow, request.leftKey());
            List<Map<String, Object>> matches = rightIndex.getOrDefault(leftKey, Collections.emptyList());
            if (matches.isEmpty() && request.joinType() == FederatedJoinRequest.JoinType.LEFT) {
                result.addRowValue(merge(leftRow, null, leftColumns, rightColumns, request));
            } else {
                for (Map<String, Object> rightRow : matches) {
                    result.addRowValue(merge(leftRow, rightRow, leftColumns, rightColumns, request));
                }
            }
        }
        return result;
    }

    private static Map<String, Object> merge(Map<String, Object> left,
                                             Map<String, Object> right,
                                             Set<String> leftColumns,
                                             Set<String> rightColumns,
                                             FederatedJoinRequest request) {
        Map<String, Object> merged = new LinkedHashMap<>();
        for (String column : leftColumns) {
            merged.put(request.leftAlias() + "." + column, value(left, column));
        }
        for (String column : rightColumns) {
            merged.put(request.rightAlias() + "." + column, right == null ? null : value(right, column));
        }
        return merged;
    }

    private static Set<String> collectColumns(List<Map<String, Object>> rows) {
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) columns.addAll(row.keySet());
        return columns;
    }

    private static Object value(Map<String, Object> row, String requested) {
        if (row == null) return null;
        if (row.containsKey(requested)) return row.get(requested);
        String normalized = requested.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) return entry.getValue();
        }
        throw new IllegalArgumentException("Column '" + requested + "' not found. Available: " + row.keySet());
    }

    private static ExecutionDomain domain(CmdDatasource datasource) {
        return new ExecutionDomain(datasource.getAreaCode(), datasource.getDatasourceType(), datasource.getDsConfName());
    }
}
