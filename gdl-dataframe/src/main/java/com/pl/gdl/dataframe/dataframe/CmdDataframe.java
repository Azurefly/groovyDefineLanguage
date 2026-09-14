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
    CmdDataframe tumbleWindow(String timeCol, String size, String unit, String... offsetAndUnit);
    CmdDataframe hopWindow(String timeCol, String slide, String sUnit, String size, String wUnit, String... offsetAndUnit);
    CmdDataframe cumulateWindow(String timeCol, String step, String stepUnit, String max, String maxUnit, String... offsetAndUnit);
    CmdDataframe periodReactor(String cronExpr);
    CmdDataframe increment(CmdDatasource ds, String querySql, String incField, int hitCount, int maxWaitSec);

    // Advanced & Script
    HttpOperator http(String method, String url);
    CmdDataframe llmCall(CmdDatasource llmDs, String model, String role, String target, String resultCol, Map<String, Object> params);
    CmdDataframe groovy(Closure<RowDataFrame> closure);

    // Properties & Execution
    String getTempTable();
    String getVAR_TEMP_TABLE();
    LogicalOperator getOperator();
    RowDataFrame getData();
    RowDataFrame collect();
}
