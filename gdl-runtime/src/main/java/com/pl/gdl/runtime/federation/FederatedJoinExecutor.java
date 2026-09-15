package com.pl.gdl.runtime.federation;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;
import com.pl.gdl.dataframe.datasource.DatasourceRegistry;
import com.pl.gdl.dataframe.engine.ExecutionEngine;
import com.pl.gdl.dataframe.engine.InMemoryEngine;
import com.pl.gdl.dataframe.federation.FederatedHashJoiner;
import com.pl.gdl.dataframe.federation.RowExchange;
import com.pl.gdl.dataframe.federation.RowExchangeRegistry;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator;
import com.pl.gdl.runtime.plan.DatasourceExecutionPlanner;
import com.pl.gdl.runtime.plan.ExecutionIntent;
import com.pl.gdl.runtime.plan.ExecutionPlan;

import java.util.List;
import java.util.Objects;

/**
 * Explicit raw-SQL federation path. Source SQL is pushed down independently,
 * materialized through the same RowExchange SPI used by ordinary DataFrame
 * joins, then joined with the shared FederatedHashJoiner.
 */
public class FederatedJoinExecutor {
    private final DatasourceRegistry registry;
    private final DatasourceExecutionPlanner planner;
    private final ExecutionEngine fallbackEngine;
    private final RowExchangeRegistry exchangeRegistry;

    public FederatedJoinExecutor() {
        this(DatasourceRegistry.getDefault(), new DatasourceExecutionPlanner(),
                new InMemoryEngine(), RowExchangeRegistry.getDefault());
    }

    public FederatedJoinExecutor(DatasourceRegistry registry,
                                 DatasourceExecutionPlanner planner,
                                 ExecutionEngine fallbackEngine) {
        this(registry, planner, fallbackEngine, RowExchangeRegistry.getDefault());
    }

    public FederatedJoinExecutor(DatasourceRegistry registry,
                                 DatasourceExecutionPlanner planner,
                                 ExecutionEngine fallbackEngine,
                                 RowExchangeRegistry exchangeRegistry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.fallbackEngine = Objects.requireNonNull(fallbackEngine, "fallbackEngine must not be null");
        this.exchangeRegistry = Objects.requireNonNull(exchangeRegistry, "exchangeRegistry must not be null");
    }

    public FederatedJoinResult execute(FederatedJoinRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        DatasourceIdentity leftIdentity = DatasourceIdentity.from(request.leftDatasource());
        DatasourceIdentity rightIdentity = DatasourceIdentity.from(request.rightDatasource());
        if (!leftIdentity.sameArea(rightIdentity)) {
            throw new UnsupportedOperationException("Cross-area federation requires REMOTE_DRIFT exchange: "
                    + leftIdentity.areaCode() + " -> " + rightIdentity.areaCode());
        }

        ExecutionPlan leftPlan = sourcePlan(request.leftDatasource());
        ExecutionPlan rightPlan = sourcePlan(request.rightDatasource());
        RowDataFrame left = executeSource(leftPlan, request.leftDatasource(), request.leftSql());
        RowDataFrame right = executeSource(rightPlan, request.rightDatasource(), request.rightSql());

        DatasourceIdentity target = new DatasourceIdentity(leftIdentity.areaCode(), "LOCAL_JOIN", "explicit");
        RowExchange leftExchange = exchangeRegistry.select(leftIdentity, target, left);
        RowExchange rightExchange = exchangeRegistry.select(rightIdentity, target, right);
        left = leftExchange.transfer(leftIdentity, target, left);
        right = rightExchange.transfer(rightIdentity, target, right);

        RowDataFrame joined = FederatedHashJoiner.join(
                left, right,
                request.leftKey(), request.rightKey(),
                request.leftAlias(), request.rightAlias(),
                request.joinType() == FederatedJoinRequest.JoinType.LEFT
                        ? JoinOperator.JoinType.LEFT : JoinOperator.JoinType.INNER);

        List<FederatedJoinResult.ExchangeTrace> exchanges = List.of(
                new FederatedJoinResult.ExchangeTrace(
                        request.leftAlias(), domain(request.leftDatasource()), left.rowSize(), mapMode(leftExchange.getMode())),
                new FederatedJoinResult.ExchangeTrace(
                        request.rightAlias(), domain(request.rightDatasource()), right.rowSize(), mapMode(rightExchange.getMode())));
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

    private static ExecutionDomain domain(CmdDatasource datasource) {
        DatasourceIdentity identity = DatasourceIdentity.from(datasource);
        return new ExecutionDomain(identity.areaCode(), identity.datasourceType(), identity.instanceKey());
    }

    private static ExchangeBoundary.MaterializationMode mapMode(RowExchange.Mode mode) {
        return switch (mode) {
            case MEMORY -> ExchangeBoundary.MaterializationMode.MEMORY;
            case INTERMEDIATE_TABLE -> ExchangeBoundary.MaterializationMode.INTERMEDIATE_TABLE;
            case REMOTE_DRIFT -> ExchangeBoundary.MaterializationMode.REMOTE_DRIFT;
            case STREAMING -> ExchangeBoundary.MaterializationMode.MEMORY;
        };
    }
}
