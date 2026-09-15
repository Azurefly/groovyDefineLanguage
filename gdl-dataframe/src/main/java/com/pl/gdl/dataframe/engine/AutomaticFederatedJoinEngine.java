package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;
import com.pl.gdl.dataframe.federation.EquiJoinCondition;
import com.pl.gdl.dataframe.federation.FederatedHashJoiner;
import com.pl.gdl.dataframe.federation.RowExchange;
import com.pl.gdl.dataframe.federation.RowExchangeRegistry;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Executes an ordinary DataFrame JoinOperator whose two branches resolve to
 * different physical datasources. It intentionally supports only direct
 * equality predicates; unsupported predicates fail rather than changing
 * semantics silently.
 */
public class AutomaticFederatedJoinEngine implements ExecutionEngine {
    private final ExecutionEngine leftEngine;
    private final ExecutionEngine rightEngine;
    private final DatasourceIdentity leftIdentity;
    private final DatasourceIdentity rightIdentity;
    private final RowExchangeRegistry exchangeRegistry;
    private final List<RowExchange.Mode> lastExchangeModes = new ArrayList<>();

    public AutomaticFederatedJoinEngine(ExecutionEngine leftEngine,
                                        ExecutionEngine rightEngine,
                                        CmdDatasource leftDatasource,
                                        CmdDatasource rightDatasource) {
        this(leftEngine, rightEngine, leftDatasource, rightDatasource, RowExchangeRegistry.getDefault());
    }

    public AutomaticFederatedJoinEngine(ExecutionEngine leftEngine,
                                        ExecutionEngine rightEngine,
                                        CmdDatasource leftDatasource,
                                        CmdDatasource rightDatasource,
                                        RowExchangeRegistry exchangeRegistry) {
        this.leftEngine = Objects.requireNonNull(leftEngine, "leftEngine must not be null");
        this.rightEngine = Objects.requireNonNull(rightEngine, "rightEngine must not be null");
        this.leftIdentity = DatasourceIdentity.from(Objects.requireNonNull(leftDatasource, "leftDatasource must not be null"));
        this.rightIdentity = DatasourceIdentity.from(Objects.requireNonNull(rightDatasource, "rightDatasource must not be null"));
        this.exchangeRegistry = Objects.requireNonNull(exchangeRegistry, "exchangeRegistry must not be null");
    }

    public DatasourceIdentity getLeftIdentity() { return leftIdentity; }
    public DatasourceIdentity getRightIdentity() { return rightIdentity; }
    public List<RowExchange.Mode> getLastExchangeModes() { return Collections.unmodifiableList(lastExchangeModes); }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        if (!(operator instanceof JoinOperator join) || join.getUpstream().size() != 2) {
            throw new IllegalArgumentException("AutomaticFederatedJoinEngine requires a binary JoinOperator");
        }
        if (!leftIdentity.sameArea(rightIdentity)) {
            throw new UnsupportedOperationException("Cross-area DataFrame join requires REMOTE_DRIFT exchange: "
                    + leftIdentity.areaCode() + " -> " + rightIdentity.areaCode());
        }

        EquiJoinCondition condition = EquiJoinCondition.parse(join.getOnCondition());
        LogicalOperator leftOperator = join.getUpstream().get(0);
        LogicalOperator rightOperator = join.getUpstream().get(1);
        RowDataFrame leftRows = leftEngine.execute(leftOperator);
        RowDataFrame rightRows = rightEngine.execute(rightOperator);

        DatasourceIdentity joinTarget = new DatasourceIdentity(leftIdentity.areaCode(), "LOCAL_JOIN", join.getOperatorId());
        lastExchangeModes.clear();
        leftRows = transfer(leftIdentity, joinTarget, leftRows);
        rightRows = transfer(rightIdentity, joinTarget, rightRows);

        return FederatedHashJoiner.join(
                leftRows,
                rightRows,
                condition.leftKey(),
                condition.rightKey(),
                alias(leftOperator, "left"),
                alias(rightOperator, "right"),
                join.getJoinType());
    }

    private RowDataFrame transfer(DatasourceIdentity source, DatasourceIdentity target, RowDataFrame rows) {
        RowExchange exchange = exchangeRegistry.select(source, target, rows);
        lastExchangeModes.add(exchange.getMode());
        return exchange.transfer(source, target, rows);
    }

    @Override
    public String toSql(LogicalOperator operator) {
        if (!(operator instanceof JoinOperator join) || join.getUpstream().size() != 2) {
            throw new IllegalArgumentException("AutomaticFederatedJoinEngine requires a binary JoinOperator");
        }
        return "/* federated join: independently pushed down */\n"
                + "-- left\n" + leftEngine.toSql(join.getUpstream().get(0)) + "\n"
                + "-- right\n" + rightEngine.toSql(join.getUpstream().get(1));
    }

    private static String alias(LogicalOperator operator, String fallback) {
        if (operator.getAlias() != null && !operator.getAlias().isBlank()) return operator.getAlias();
        return fallback;
    }
}
