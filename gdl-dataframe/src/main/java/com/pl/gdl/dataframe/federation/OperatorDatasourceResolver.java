package com.pl.gdl.dataframe.federation;

import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.datasource.DatasourceIdentity;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.dataframe.operator.base.QueryOperator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Resolves a logical branch when all source leaves belong to one physical datasource. */
public final class OperatorDatasourceResolver {
    private OperatorDatasourceResolver() {}

    public static Optional<CmdDatasource> resolveSingle(LogicalOperator operator) {
        Map<DatasourceIdentity, CmdDatasource> sources = new LinkedHashMap<>();
        collect(operator, sources);
        return sources.size() == 1 ? Optional.of(sources.values().iterator().next()) : Optional.empty();
    }

    private static void collect(LogicalOperator operator, Map<DatasourceIdentity, CmdDatasource> sources) {
        if (operator == null) return;
        if (operator instanceof QueryOperator query && query.getDatasource() != null) {
            add(query.getDatasource(), sources);
            return;
        }
        if (operator instanceof FromOperator from && from.getDatasource() != null) {
            add(from.getDatasource(), sources);
            return;
        }
        for (LogicalOperator upstream : operator.getUpstream()) {
            collect(upstream, sources);
        }
    }

    private static void add(CmdDatasource datasource, Map<DatasourceIdentity, CmdDatasource> sources) {
        sources.putIfAbsent(DatasourceIdentity.from(datasource), datasource);
    }
}
