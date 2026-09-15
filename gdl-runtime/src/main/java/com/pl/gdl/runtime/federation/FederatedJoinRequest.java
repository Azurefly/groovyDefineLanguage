package com.pl.gdl.runtime.federation;

import com.pl.gdl.dataframe.datasource.CmdDatasource;

import java.util.Objects;

public record FederatedJoinRequest(
        CmdDatasource leftDatasource,
        String leftSql,
        String leftKey,
        String leftAlias,
        CmdDatasource rightDatasource,
        String rightSql,
        String rightKey,
        String rightAlias,
        JoinType joinType
) {
    public enum JoinType { INNER, LEFT }

    public FederatedJoinRequest {
        Objects.requireNonNull(leftDatasource, "leftDatasource must not be null");
        Objects.requireNonNull(rightDatasource, "rightDatasource must not be null");
        if (leftSql == null || leftSql.isBlank()) throw new IllegalArgumentException("leftSql must not be blank");
        if (rightSql == null || rightSql.isBlank()) throw new IllegalArgumentException("rightSql must not be blank");
        if (leftKey == null || leftKey.isBlank()) throw new IllegalArgumentException("leftKey must not be blank");
        if (rightKey == null || rightKey.isBlank()) throw new IllegalArgumentException("rightKey must not be blank");
        leftAlias = leftAlias == null || leftAlias.isBlank() ? "left" : leftAlias.trim();
        rightAlias = rightAlias == null || rightAlias.isBlank() ? "right" : rightAlias.trim();
        joinType = joinType == null ? JoinType.INNER : joinType;
    }
}
