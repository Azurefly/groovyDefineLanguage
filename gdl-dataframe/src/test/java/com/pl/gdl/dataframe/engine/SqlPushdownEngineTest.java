package com.pl.gdl.dataframe.engine;

import com.pl.gdl.dataframe.dialect.H2SqlDialect;
import com.pl.gdl.dataframe.dialect.HiveSqlDialect;
import com.pl.gdl.dataframe.operator.LogicalOperator;
import com.pl.gdl.dataframe.operator.base.AliasOperator;
import com.pl.gdl.dataframe.operator.base.DistinctOperator;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.dataframe.operator.base.GroupOperator;
import com.pl.gdl.dataframe.operator.base.GroupSortFirstOperator;
import com.pl.gdl.dataframe.operator.base.InsertOperator;
import com.pl.gdl.dataframe.operator.base.LimitOperator;
import com.pl.gdl.dataframe.operator.base.MappingOperator;
import com.pl.gdl.dataframe.operator.base.QueryOperator;
import com.pl.gdl.dataframe.operator.base.SelectOperator;
import com.pl.gdl.dataframe.operator.base.SortOperator;
import com.pl.gdl.dataframe.operator.base.WhereOperator;
import com.pl.gdl.dataframe.operator.base.WithColumnOperator;
import com.pl.gdl.dataframe.operator.join.ExistsOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator;
import com.pl.gdl.dataframe.operator.join.JoinOperator.JoinType;
import com.pl.gdl.dataframe.operator.output.ToOperator;
import com.pl.gdl.dataframe.operator.set.IntersectOperator;
import com.pl.gdl.dataframe.operator.set.SubtractOperator;
import com.pl.gdl.dataframe.operator.set.UnionOperator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SqlPushdownEngine 各算子 SQL 生成测试，以及未知算子 / Hive 不支持 ALL 集合算子的失败测试。
 */
public class SqlPushdownEngineTest {

    private static final SqlPushdownEngine HIVE = new SqlPushdownEngine(new HiveSqlDialect());
    private static final SqlPushdownEngine H2 = new SqlPushdownEngine(new H2SqlDialect());

    private static LogicalOperator from(String table) {
        return new FromOperator(null, table);
    }

    /** 测试用的未知算子类型（引擎没有对应分支）。 */
    private static class UnknownOperator extends LogicalOperator {
        @Override
        public String getOperatorName() {
            return "unknown";
        }
    }

    @Test
    public void testFrom() {
        assertThat(HIVE.toSql(from("t_user"))).isEqualTo("SELECT * FROM t_user");
    }

    @Test
    public void testAlias() {
        assertThat(HIVE.toSql(new AliasOperator(from("t_user"), "u")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) AS u");
    }

    @Test
    public void testAliasBlankPassthrough() {
        assertThat(HIVE.toSql(new AliasOperator(from("t_user"), " ")))
                .isEqualTo("SELECT * FROM t_user");
        assertThat(HIVE.toSql(new AliasOperator(from("t_user"), null)))
                .isEqualTo("SELECT * FROM t_user");
    }

    @Test
    public void testQuery() {
        assertThat(HIVE.toSql(new QueryOperator(null, "select 1"))).isEqualTo("select 1");
    }

    @Test
    public void testWhere() {
        assertThat(HIVE.toSql(new WhereOperator(from("t_user"), "age > 18")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) sub_where WHERE age > 18");
    }

    @Test
    public void testSelect() {
        assertThat(HIVE.toSql(new SelectOperator(from("t_user"), "id", "name")))
                .isEqualTo("SELECT id, name FROM (SELECT * FROM t_user) sub_select");
    }

    @Test
    public void testMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("user_name", "name");
        assertThat(HIVE.toSql(new MappingOperator(from("t_user"), mapping)))
                .isEqualTo("SELECT name AS user_name FROM (SELECT * FROM t_user) sub_mapping");
    }

    @Test
    public void testWithColumn() {
        assertThat(HIVE.toSql(new WithColumnOperator(from("t_user"), "age2", "age + 1", null)))
                .isEqualTo("SELECT *, age + 1 AS age2 FROM (SELECT * FROM t_user) sub_with");
    }

    @Test
    public void testGroup() {
        assertThat(HIVE.toSql(new GroupOperator(from("t_user"), "dept", "count(*) AS cnt")))
                .isEqualTo("SELECT dept, count(*) AS cnt FROM (SELECT * FROM t_user) sub_group GROUP BY dept");
    }

    @Test
    public void testSort() {
        assertThat(HIVE.toSql(new SortOperator(from("t_user"), "age desc")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) sub_sort ORDER BY age desc");
    }

    @Test
    public void testLimitH2() {
        assertThat(H2.toSql(new LimitOperator(from("t_user"), 10)))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) sub_limit LIMIT 10");
        assertThat(H2.toSql(new LimitOperator(from("t_user"), 5, 10)))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) sub_limit LIMIT 10 OFFSET 5");
    }

    @Test
    public void testLimitHive() {
        assertThat(HIVE.toSql(new LimitOperator(from("t_user"), 5, 10)))
                .isEqualTo("SELECT * FROM (SELECT * FROM t_user) sub_limit LIMIT 5, 10");
    }

    @Test
    public void testDistinct() {
        assertThat(HIVE.toSql(new DistinctOperator(from("t_user"))))
                .isEqualTo("SELECT DISTINCT * FROM (SELECT * FROM t_user) sub_distinct");
        assertThat(HIVE.toSql(new DistinctOperator(from("t_user"), "dept")))
                .isEqualTo("SELECT DISTINCT dept FROM (SELECT * FROM t_user) sub_distinct");
    }

    @Test
    public void testGroupSortFirstHive() {
        assertThat(HIVE.toSql(new GroupSortFirstOperator(from("t_user"), "dept", "age desc")))
                .isEqualTo("SELECT * FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY age desc) AS rn "
                        + "FROM (SELECT * FROM t_user) sub_gsf) t WHERE t.rn = 1");
    }

    @Test
    public void testUnion() {
        assertThat(HIVE.toSql(new UnionOperator(from("t1"), from("t2"), false)))
                .isEqualTo("(SELECT * FROM t1) UNION (SELECT * FROM t2)");
        assertThat(HIVE.toSql(new UnionOperator(from("t1"), from("t2"), true)))
                .isEqualTo("(SELECT * FROM t1) UNION ALL (SELECT * FROM t2)");
    }

    @Test
    public void testSubtractH2() {
        assertThat(H2.toSql(new SubtractOperator(from("t1"), from("t2"), false)))
                .isEqualTo("(SELECT * FROM t1) EXCEPT (SELECT * FROM t2)");
    }

    @Test
    public void testIntersectH2() {
        assertThat(H2.toSql(new IntersectOperator(from("t1"), from("t2"), false)))
                .isEqualTo("(SELECT * FROM t1) INTERSECT (SELECT * FROM t2)");
    }

    @Test
    public void testHiveSubtractAllThrows() {
        assertThatThrownBy(() -> HIVE.toSql(new SubtractOperator(from("t1"), from("t2"), true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testHiveIntersectAllThrows() {
        assertThatThrownBy(() -> HIVE.toSql(new IntersectOperator(from("t1"), from("t2"), true)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testJoin() {
        assertThat(HIVE.toSql(new JoinOperator(from("t1"), from("t2"), JoinType.INNER, "t1.id = t2.id")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t1) left_tbl JOIN (SELECT * FROM t2) right_tbl ON t1.id = t2.id");
        assertThat(HIVE.toSql(new JoinOperator(from("t1"), from("t2"), JoinType.LEFT, "t1.id = t2.id")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t1) left_tbl LEFT JOIN (SELECT * FROM t2) right_tbl ON t1.id = t2.id");
    }

    @Test
    public void testExists() {
        assertThat(HIVE.toSql(new ExistsOperator(from("t1"), from("t2"), false, "t1.id = t2.id")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t1) a WHERE EXISTS "
                        + "(SELECT 1 FROM (SELECT * FROM t2) b WHERE t1.id = t2.id)");
        assertThat(HIVE.toSql(new ExistsOperator(from("t1"), from("t2"), true, "t1.id = t2.id")))
                .isEqualTo("SELECT * FROM (SELECT * FROM t1) a WHERE NOT EXISTS "
                        + "(SELECT 1 FROM (SELECT * FROM t2) b WHERE t1.id = t2.id)");
    }

    @Test
    public void testToInsert() {
        assertThat(HIVE.toSql(new ToOperator(from("t1"), null, "t_target")))
                .isEqualTo("INSERT INTO t_target SELECT * FROM t1");
    }

    @Test
    public void testToOverwriteHive() {
        ToOperator op = new ToOperator(from("t1"), null, "t_target");
        op.setOverwrite(true);
        assertThat(HIVE.toSql(op))
                .isEqualTo("INSERT OVERWRITE TABLE t_target SELECT * FROM t1");
    }

    @Test
    public void testInsert() {
        assertThat(HIVE.toSql(new InsertOperator(null, "t", "INSERT INTO t VALUES (1)")))
                .isEqualTo("INSERT INTO t VALUES (1)");
    }

    @Test
    public void testUnknownOperatorThrows() {
        assertThatThrownBy(() -> HIVE.toSql(new UnknownOperator()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void testChainedOperators() {
        LogicalOperator op = new LimitOperator(
                new SortOperator(
                        new WhereOperator(from("t_user"), "age > 18"),
                        "age desc"),
                0, 10);
        assertThat(HIVE.toSql(op)).isEqualTo(
                "SELECT * FROM (SELECT * FROM (SELECT * FROM (SELECT * FROM t_user) sub_where WHERE age > 18) "
                        + "sub_sort ORDER BY age desc) sub_limit LIMIT 10");
    }
}
