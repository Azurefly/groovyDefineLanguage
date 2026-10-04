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

        // FROM：SELECT * FROM 表名 [AS 别名]
        if (operator instanceof FromOperator fromOp) {
            String alias = fromOp.getAlias() != null ? " AS " + fromOp.getAlias() : "";
            return "SELECT * FROM " + fromOp.getTableName() + alias;
        }

        // ALIAS：SELECT * FROM (上游) AS 别名；别名为空时直接透传上游 SQL
        if (operator instanceof AliasOperator aliasOp) {
            String base = toSql(aliasOp.getUpstream().get(0));
            String aliasName = aliasOp.getAliasName();
            if (aliasName == null || aliasName.isBlank()) {
                return base;
            }
            return "SELECT * FROM (" + base + ") AS " + aliasName;
        }

        // QUERY：直接使用用户提供的原生 SQL
        if (operator instanceof QueryOperator queryOp) {
            return queryOp.getQuerySql();
        }

        // WHERE：SELECT * FROM (上游) sub_where WHERE 条件
        if (operator instanceof WhereOperator whereOp) {
            String base = toSql(whereOp.getUpstream().get(0));
            return "SELECT * FROM (" + base + ") sub_where WHERE " + whereOp.getCondition();
        }

        // SELECT：SELECT 表达式列表 FROM (上游) sub_select
        if (operator instanceof SelectOperator selectOp) {
            String base = toSql(selectOp.getUpstream().get(0));
            return "SELECT " + String.join(", ", selectOp.getExpressions()) + " FROM (" + base + ") sub_select";
        }

        // MAPPING：SELECT 表达式 AS 新列名, ... FROM (上游) sub_mapping
        if (operator instanceof MappingOperator mappingOp) {
            String base = toSql(mappingOp.getUpstream().get(0));
            StringJoiner sj = new StringJoiner(", ");
            for (Map.Entry<String, String> entry : mappingOp.getMapping().entrySet()) {
                sj.add(entry.getValue() + " AS " + entry.getKey());
            }
            return "SELECT " + sj + " FROM (" + base + ") sub_mapping";
        }

        // WITH_COLUMN：SELECT *, 表达式 AS 新列 FROM (上游) sub_with
        if (operator instanceof WithColumnOperator withOp) {
            String base = toSql(withOp.getUpstream().get(0));
            return "SELECT *, " + withOp.getExpression() + " AS " + withOp.getColumnName() + " FROM (" + base + ") sub_with";
        }

        // GROUP：SELECT 分组列, 聚合表达式 FROM (上游) sub_group GROUP BY 分组列
        if (operator instanceof GroupOperator groupOp) {
            String base = toSql(groupOp.getUpstream().get(0));
            return "SELECT " + groupOp.getGroupByCols() + ", " + groupOp.getAggregateExprs() +
                    " FROM (" + base + ") sub_group GROUP BY " + groupOp.getGroupByCols();
        }

        // SORT：SELECT * FROM (上游) sub_sort ORDER BY 排序表达式；
        // 需要行号时再包一层 ROW_NUMBER() OVER (ORDER BY ...) AS 索引列
        if (operator instanceof SortOperator sortOp) {
            String base = toSql(sortOp.getUpstream().get(0));
            // 空排序表达式时（如纯 index 场景），ORDER BY 子句为空
            String orderClause = sortOp.getSortExpressions().isEmpty() ? ""
                    : " ORDER BY " + String.join(", ", sortOp.getSortExpressions());
            if (sortOp.getIndexColumnName() != null) {
                String overClause = orderClause.isEmpty() ? "()" : "(" + orderClause + ")";
                return "SELECT *, ROW_NUMBER() OVER " + overClause + " AS " + sortOp.getIndexColumnName() +
                        " FROM (" + base + ") sub_sort" + orderClause;
            }
            return "SELECT * FROM (" + base + ") sub_sort" + orderClause;
        }

        // DISTRIBUTE SORT：Hive 的 DISTRIBUTE BY + SORT BY。
        // 单机内存引擎无"分区"概念，降级为全局 ORDER BY（先分区列，后排序列）。
        // 注意：语义与 Hive 不完全等同（Hive 只保证分区内有序），文档已说明。
        if (operator instanceof DistributeSortOperator dsOp) {
            String base = toSql(dsOp.getUpstream().get(0));
            String orderBy = dsOp.getPartitionCols();
            if (dsOp.getSortCols() != null && !dsOp.getSortCols().isBlank()) {
                orderBy += ", " + dsOp.getSortCols();
            }
            return "SELECT * FROM (" + base + ") sub_ds ORDER BY " + orderBy;
        }

        // LIMIT：SELECT * FROM (上游) sub_limit + 方言分页子句
        if (operator instanceof LimitOperator limitOp) {
            String base = toSql(limitOp.getUpstream().get(0));
            return "SELECT * FROM (" + base + ") sub_limit " + dialect.formatLimit(limitOp.getOffset(), limitOp.getLimit());
        }

        // SAMPLE：随机采样。按行数用 ORDER BY rand + 方言分页，按比例用 WHERE rand < fraction
        // seed 不为空时透传，保证可复现；随机函数名按方言适配（如 PG 用 RANDOM()）
        if (operator instanceof SampleOperator sampleOp) {
            String base = toSql(sampleOp.getUpstream().get(0));
            String randExpr = dialect.formatRandom(sampleOp.getSeed());
            if (sampleOp.isBySize()) {
                return "SELECT * FROM (" + base + ") sub_sample ORDER BY " + randExpr + " " + dialect.formatLimit(0, sampleOp.getSampleSize());
            } else {
                return "SELECT * FROM (" + base + ") sub_sample WHERE " + randExpr + " < " + sampleOp.getFraction();
            }
        }

        // DISTINCT：无列时 SELECT DISTINCT *，否则 SELECT DISTINCT 列, ...（子查询包一层）
        if (operator instanceof DistinctOperator distinctOp) {
            String base = toSql(distinctOp.getUpstream().get(0));
            if (distinctOp.getDistinctColumns().isEmpty()) {
                return "SELECT DISTINCT * FROM (" + base + ") sub_distinct";
            } else {
                return "SELECT DISTINCT " + String.join(", ", distinctOp.getDistinctColumns()) + " FROM (" + base + ") sub_distinct";
            }
        }

        // GROUP_SORT_FIRST：分组取首行，由方言拼窗口函数
        if (operator instanceof GroupSortFirstOperator gsfOp) {
            String base = toSql(gsfOp.getUpstream().get(0));
            return dialect.formatGroupSortFirst(gsfOp.getGroupCols(), gsfOp.getSortCols(), "(" + base + ") sub_gsf");
        }

        // UNION：(左) UNION [ALL] (右)
        if (operator instanceof UnionOperator unionOp) {
            String left = toSql(unionOp.getUpstream().get(0));
            String right = toSql(unionOp.getUpstream().get(1));
            String kw = unionOp.isAll() ? " UNION ALL " : " UNION ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        // SUBTRACT：(左) EXCEPT [ALL] (右)；Hive 不支持 EXCEPT ALL，直接失败而非生成非法 SQL
        if (operator instanceof SubtractOperator subOp) {
            if (subOp.isAll() && dialect instanceof HiveSqlDialect) {
                throw new UnsupportedOperationException("Hive 方言不支持 EXCEPT ALL（subtractAll）");
            }
            String left = toSql(subOp.getUpstream().get(0));
            String right = toSql(subOp.getUpstream().get(1));
            String kw = subOp.isAll() ? " EXCEPT ALL " : " EXCEPT ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        // INTERSECT：(左) INTERSECT [ALL] (右)；Hive 不支持 INTERSECT ALL，直接失败
        if (operator instanceof IntersectOperator intersectOp) {
            if (intersectOp.isAll() && dialect instanceof HiveSqlDialect) {
                throw new UnsupportedOperationException("Hive 方言不支持 INTERSECT ALL（intersectAll）");
            }
            String left = toSql(intersectOp.getUpstream().get(0));
            String right = toSql(intersectOp.getUpstream().get(1));
            String kw = intersectOp.isAll() ? " INTERSECT ALL " : " INTERSECT ";
            return "(" + left + ")" + kw + "(" + right + ")";
        }

        // JOIN：SELECT * FROM (左) left_tbl 连接类型 (右) right_tbl ON 条件
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

        // EXISTS：SELECT * FROM (左) a WHERE [NOT] EXISTS (SELECT 1 FROM (右) b WHERE 条件)
        if (operator instanceof ExistsOperator existsOp) {
            String left = toSql(existsOp.getUpstream().get(0));
            String right = toSql(existsOp.getUpstream().get(1));
            String kw = existsOp.isNot() ? "NOT EXISTS" : "EXISTS";
            return "SELECT * FROM (" + left + ") a WHERE " + kw + " (SELECT 1 FROM (" + right + ") b WHERE " + existsOp.getOnCondition() + ")";
        }

        // TO：INSERT [OVERWRITE] [PARTITION] INTO 目标表 上游查询；覆盖逻辑交由方言拼写
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

        // INSERT：直接使用用户提供的原生 INSERT 语句
        if (operator instanceof InsertOperator insertOp) {
            return insertOp.getInsertSql();
        }

        // 未知算子：不再静默透传上游或拼临时表名，直接失败，避免生成语义错误的 SQL
        throw new UnsupportedOperationException(
                "不支持的算子类型，无法生成 SQL："
                        + (operator == null ? "null" : operator.getClass().getName()));
    }
}
