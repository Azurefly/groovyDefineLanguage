package com.pl.gdl.dataframe.engine;

import com.pl.gdl.common.model.RowDataFrame;
import com.pl.gdl.dataframe.dialect.HiveSqlDialect;
import com.pl.gdl.dataframe.dialect.SqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.*;
import com.pl.gdl.dataframe.operator.join.ExistsOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator;
import com.pl.gdl.dataframe.operator.output.ToOperator;
import com.pl.gdl.dataframe.operator.set.IntersectOperator;
import com.pl.gdl.dataframe.operator.set.SubtractOperator;
import com.pl.gdl.dataframe.operator.set.UnionOperator;

import java.util.Map;
import java.util.StringJoiner;

public class SqlPushdownEngine implements ExecutionEngine {
    private final SqlDialect dialect;

    public SqlPushdownEngine() {
        this(new HiveSqlDialect());
    }

    public SqlPushdownEngine(SqlDialect dialect) {
        this.dialect = dialect != null ? dialect : new HiveSqlDialect();
    }

    public SqlDialect getDialect() {
        return dialect;
    }

    @Override
    public RowDataFrame execute(LogicalOperator operator) {
        return new RowDataFrame();
    }

    @Override
    public String toSql(LogicalOperator operator) {
        if (operator == null) return "";

        if (operator instanceof FromOperator fromOp) {
            String alias = fromOp.getAlias() != null ? " AS " + fromOp.getAlias() : "";
            return "SELECT * FROM " + fromOp.getTableName() + alias;
        }

        if (operator instanceof QueryOperator queryOp) {
            return queryOp.getQuerySql();
        }

        if (operator instanceof WhereOperator whereOp) {
            String base = toSql(whereOp.getUpstream().get(0));
            return "SELECT * FROM (" + base + ") sub_where WHERE " + whereOp.getCondition();
        }

        if (operator instanceof SelectOperator selectOp) {
            String base = toSql(selectOp.getUpstream().get(0));
            return "SELECT " + String.join(", ", selectOp.getExpressions()) + " FROM (" + base + ") sub_select";
        }

        if (operator instanceof MappingOperator mappingOp) {
            String base = toSql(mappingOp.getUpstream().get(0));
            StringJoiner sj = new StringJoiner(", ");
            for (Map.Entry<String, String> entry : mappingOp.getMapping().entrySet()) {
                sj.add(entry.getValue() + " AS " + entry.getKey());
            }
            return "SELECT " + sj + " FROM (" + base + ") sub_mapping";
        }

        if (operator instanceof WithColumnOperator withOp) {
            String base = toSql(withOp.getUpstream().get(0));
            return "SELECT *, " + withOp.getExpression() + " AS " + withOp.getColumnName() + " FROM (" + base + ") sub_with";
        }

        if (operator instanceof GroupOperator groupOp) {
            String base = toSql(groupOp.getUpstream().get(0));
            return "SELECT " + groupOp.getGroupByCols() + ", " + groupOp.getAggregateExprs() +
                    " FROM (" + base + ") sub_group GROUP BY " + groupOp.getGroupByCols();
        }

        if (operator instanceof SortOperator sortOp) {
            String base = toSql(sortOp.getUpstream().get(0));
            String orderClause = " ORDER BY " + String.join(", ", sortOp.getSortExpressions());
            if (sortOp.getIndexColumnName() != null) {
                return "SELECT *, ROW_NUMBER() OVER (" + orderClause + ") AS " + sortOp.getIndexColumnName() +
                        " FROM (" + base + ") sub_sort" + orderClause;
            }
            return "SELECT * FROM (" + base + ") sub_sort" + orderClause;
        }

        if (operator instanceof LimitOperator limitOp) {
            String base = toSql(limitOp.getUpstream().get(0));
            return "SELECT * FROM (" + base + ") sub_limit " + dialect.formatLimit(limitOp.getOffset(), limitOp.getLimit());
        }

        if (operator instanceof DistinctOperator distinctOp) {
            String base = toSql(distinctOp.getUpstream().get(0));
            if (distinctOp.getDistinctColumns().isEmpty()) {
                return "SELECT DISTINCT * FROM (" + base + ") sub_distinct";
            } else {
                return "SELECT DISTINCT " + String.join(", ", distinctOp.getDistinctColumns()) + " FROM (" + base + ") sub_distinct";
            }
        }

        if (operator instanceof GroupSortFirstOperator gsfOp) {
            String base = toSql(gsfOp.getUpstream().get(0));
            return dialect.formatGroupSortFirst(gsfOp.getGroupCols(), gsfOp.getSortCols(), "(" + base + ") sub_gsf");
        }

        if (operator instanceof UnionOperator unionOp) {
            String left = toSql(unionOp.getUpstream().get(0));
            String right = toSql(unionOp.getUpstream().get(1));
            String kw = unionOp.isAll() ? " UNION ALL " : " UNION ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        if (operator instanceof SubtractOperator subOp) {
            String left = toSql(subOp.getUpstream().get(0));
            String right = toSql(subOp.getUpstream().get(1));
            String kw = subOp.isAll() ? " EXCEPT ALL " : " EXCEPT ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        if (operator instanceof IntersectOperator intersectOp) {
            String left = toSql(intersectOp.getUpstream().get(0));
            String right = toSql(intersectOp.getUpstream().get(1));
            String kw = intersectOp.isAll() ? " INTERSECT ALL " : " INTERSECT ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        if (operator instanceof JoinOperator joinOp) {
            String left = toSql(joinOp.getUpstream().get(0));
            String right = toSql(joinOp.getUpstream().get(1));
            String type = switch (joinOp.getJoinType()) {
                case LEFT -> "LEFT JOIN";
                case RIGHT -> "RIGHT JOIN";
                case FULL -> "FULL OUTER JOIN";
                default -> "JOIN";
            };
            return "SELECT * FROM (" + left + ") left_tbl " + type + " (" + right + ") right_tbl ON " + joinOp.getOnCondition();
        }

        if (operator instanceof ExistsOperator existsOp) {
            String left = toSql(existsOp.getUpstream().get(0));
            String right = toSql(existsOp.getUpstream().get(1));
            String kw = existsOp.isNot() ? "NOT EXISTS" : "EXISTS";
            return "SELECT * FROM (" + left + ") a WHERE " + kw + " (SELECT 1 FROM (" + right + ") b WHERE " + existsOp.getOnCondition() + ")";
        }

        if (operator instanceof ToOperator toOp) {
            String selectSql = toSql(toOp.getUpstream().get(0));
            if (toOp.isOverwrite()) {
                if (toOp.isOverwritePartition() && toOp.getPartitionSpec() != null) {
                    return dialect.formatOverwritePartition(toOp.getTargetTableName(), toOp.getPartitionSpec(), selectSql);
                }
                return dialect.formatOverwriteTable(toOp.getTargetTableName(), selectSql);
            }
            if (toOp.getPartitionSpec() != null) {
                return "INSERT INTO " + toOp.getTargetTableName() + " PARTITION (" + toOp.getPartitionSpec() + ") " + selectSql;
            }
            return "INSERT INTO " + toOp.getTargetTableName() + " " + selectSql;
        }

        if (operator instanceof InsertOperator insertOp) {
            return insertOp.getInsertSql();
        }

        if (!operator.getUpstream().isEmpty()) {
            return toSql(operator.getUpstream().get(0));
        }

        return "SELECT * FROM " + operator.getTempTableName();
    }
}
