package com.pl.gdl.ontology.model;

import com.pl.gdl.common.exception.OntologyValidationException;
import com.pl.gdl.dataframe.dataframe.CmdDataframe;
import com.pl.gdl.ontology.annotation.Column;
import com.pl.gdl.dataframe.dataframe.CmdDataframeImpl;
import com.pl.gdl.dataframe.datasource.CmdDatasource;
import com.pl.gdl.dataframe.operator.base.FromOperator;
import com.pl.gdl.runtime.script.GdlExecutionContext;
import groovy.lang.GroovyInterceptable;
import groovy.lang.GroovyObjectSupport;

import java.lang.reflect.Field;
import java.util.*;

public abstract class Ontology extends GroovyObjectSupport implements GroovyInterceptable {
    public String oId;
    public String oName;
    public String oDesc;
    public String oTable;
    public String oAuthor;
    public CmdDatasource oDs;
    public CmdDataframe oDataframe;
    public CmdDataframe queryDataframe;

    public CmdDataframe[] depends;
    public List<String> areaCodes = new ArrayList<>();
    private int callDepth = 0;

    public Ontology() {}

    public void validate() {
        if (oId == null || oId.isBlank()) throw new OntologyValidationException("oId is mandatory in " + getClass().getName());
        if (oName == null || oName.isBlank()) throw new OntologyValidationException("oName is mandatory in " + getClass().getName());
        if (oDesc == null || oDesc.isBlank()) throw new OntologyValidationException("oDesc is mandatory in " + getClass().getName());
        if (oAuthor == null || oAuthor.isBlank()) throw new OntologyValidationException("oAuthor is mandatory in " + getClass().getName());
    }

    public CmdDataframe loadData() {
        if (oTable != null && oDs != null) {
            oDataframe = from(oDs, oTable);
            if (depends != null && depends.length > 0) {
                oDataframe.depend(depends);
            }
        }
        return oDataframe;
    }

    public CmdDataframe loadData(CmdDataframe outerData) {
        this.oDataframe = outerData;
        if (oTable != null && oDs != null && outerData != null) {
            this.oDataframe = outerData.to(oDs, oTable);
        }
        return this.oDataframe;
    }

    public CmdDataframe genODataframe() {
        if (oDataframe != null) {
            return oDataframe;
        }
        if (oTable != null && oDs != null) {
            return from(oDs, oTable);
        }
        return null;
    }

    public void toOtherOntology(Map<String, String> mapping, Ontology other) {
        if (other == null) return;
        CmdDataframe sourceDf = genODataframe();
        if (sourceDf != null && mapping != null && !mapping.isEmpty()) {
            CmdDataframe mapped = sourceDf.mapping(mapping);
            other.loadData(mapped);
        }
    }

    public Ontology depend(CmdDataframe... depends) {
        this.depends = depends;
        return this;
    }

    public Ontology areaCode(String... codes) {
        if (codes != null) {
            this.areaCodes.addAll(Arrays.asList(codes));
        }
        return this;
    }

    public CmdDataframe getQueryDataframe(String... codes) {
        if (queryDataframe == null) {
            queryDataframe = genODataframe();
        }
        return queryDataframe;
    }

    public void returnDf() {
        if (queryDataframe == null) {
            queryDataframe = genODataframe();
        }
        if (queryDataframe != null) {
            GdlExecutionContext.get().setReturnDf(queryDataframe);
        }
    }

    // Query methods delegation
    private void initQuery() {
        if (queryDataframe == null) {
            queryDataframe = genODataframe();
        }
    }

    public Ontology where(String condition) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.where(mapAttributes(condition));
        }
        return this;
    }

    public Ontology select(String... expressions) {
        initQuery();
        if (queryDataframe != null && expressions != null) {
            String[] mapped = new String[expressions.length];
            for (int i = 0; i < expressions.length; i++) {
                mapped[i] = mapAttributes(expressions[i]);
            }
            queryDataframe = queryDataframe.select(mapped);
        }
        return this;
    }

    public Ontology mapping(Map<String, String> mapping) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.mapping(mapping);
        }
        return this;
    }

    public Ontology sort(String... sortExprs) {
        initQuery();
        if (queryDataframe != null && sortExprs != null) {
            String[] mapped = new String[sortExprs.length];
            for (int i = 0; i < sortExprs.length; i++) {
                mapped[i] = mapAttributes(sortExprs[i]);
            }
            queryDataframe = queryDataframe.sort(mapped);
        }
        return this;
    }

    public Ontology index(String rowNumCol) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.index(rowNumCol);
        }
        return this;
    }

    public Ontology limit(int limit) {
        return limit(0, limit);
    }

    public Ontology limit(int offset, int limit) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.limit(offset, limit);
        }
        return this;
    }

    public Ontology distinct(String... cols) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.distinct(cols);
        }
        return this;
    }

    public Ontology to(CmdDatasource ds, String tableName) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.to(ds, tableName);
        }
        return this;
    }

    public Ontology nodeId(String nodeId) {
        initQuery();
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.nodeId(nodeId);
        }
        return this;
    }

    // Storage mutations：基于 JDBC 直接写物理表，record 的 key 支持本体属性名（自动映射物理列）
    private com.pl.gdl.dataframe.engine.JdbcExecutionEngine writeEngine() {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table");
        com.pl.gdl.dataframe.engine.ExecutionEngine engine = null;
        try {
            engine = com.pl.gdl.runtime.script.GdlExecutionContext.get().getExecutionEngine();
        } catch (Exception ignored) {
        }
        if (engine instanceof com.pl.gdl.dataframe.engine.JdbcExecutionEngine jdbc) {
            return jdbc;
        }
        // 上下文非 JDBC（如 GDL 脚本默认 InMemoryEngine）时，尝试按本体数据源创建
        if (oDs instanceof com.pl.gdl.dataframe.datasource.JdbcDatasource) {
            com.pl.gdl.dataframe.engine.ExecutionEngine created =
                    com.pl.gdl.dataframe.datasource.DatasourceRegistry.getDefault().createExecutionEngine(oDs);
            if (created instanceof com.pl.gdl.dataframe.engine.JdbcExecutionEngine jdbc2) {
                return jdbc2;
            }
        }
        throw new UnsupportedOperationException(
                "Ontology 写操作需要 JDBC 数据源，当前数据源类型不支持: " +
                        (oDs == null ? "null" : oDs.getClass().getSimpleName()));
    }

    public Ontology save(Map<String, Object> record) {
        if (record == null || record.isEmpty()) throw new IllegalArgumentException("save record 不能为空");
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        Map<String, String> attrMap = attributeColumnMap();
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : record.entrySet()) {
            columns.add(attrMap.getOrDefault(e.getKey(), e.getKey()));
            values.add(e.getValue());
        }
        String sql = "INSERT INTO " + oTable + " (" + String.join(", ", columns) + ") VALUES (" +
                String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        engine.executeUpdate(sql, values);
        return this;
    }

    public Ontology delete(String expr) {
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        String where = mapAttributes(expr);
        String sql = "DELETE FROM " + oTable + (where == null || where.isBlank() ? "" : " WHERE " + where);
        engine.executeUpdate(sql, Collections.emptyList());
        return this;
    }

    /**
     * 在事务中执行业务逻辑。闭包正常返回则提交，抛异常则回滚。
     * 嵌套调用时合并到外层事务（不开启新事务）。
     * Groovy 脚本中可直接传闭包：{@code po.transaction { po.save(h); lines.each { li.save(it) } }}
     */
    public <T> T transaction(java.util.function.Supplier<T> work) {
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        boolean outer = engine.inTransaction();
        if (!outer) engine.beginTransaction();
        try {
            T result = work.get();
            if (!outer) engine.commitTransaction();
            return result;
        } catch (RuntimeException e) {
            if (!outer) engine.rollbackTransaction();
            throw e;
        } catch (Exception e) {
            if (!outer) engine.rollbackTransaction();
            throw new RuntimeException(e);
        }
    }

    /** 无返回值的事务便捷版 */
    public void transaction(Runnable work) {
        transaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * 批量插入。record 的 key 支持本体属性名（自动映射物理列）。
     * 返回每条的影响行数数组。
     */
    public int[] saveBatch(java.util.List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) return new int[0];
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        Map<String, String> attrMap = attributeColumnMap();
        List<String> columns = new ArrayList<>();
        List<java.util.List<Object>> batchParams = new ArrayList<>();
        boolean first = true;
        for (Map<String, Object> record : records) {
            List<Object> values = new ArrayList<>();
            for (Map.Entry<String, Object> e : record.entrySet()) {
                if (first) columns.add(attrMap.getOrDefault(e.getKey(), e.getKey()));
                values.add(e.getValue());
            }
            first = false;
            batchParams.add(values);
        }
        String sql = "INSERT INTO " + oTable + " (" + String.join(", ", columns) + ") VALUES (" +
                String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        return engine.executeBatch(sql, batchParams);
    }

    public Ontology update(String expr, Map<String, Object> record) {
        if (record == null || record.isEmpty()) throw new IllegalArgumentException("update record 不能为空");
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        Map<String, String> attrMap = attributeColumnMap();
        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, Object> e : record.entrySet()) {
            sets.add(attrMap.getOrDefault(e.getKey(), e.getKey()) + " = ?");
            values.add(e.getValue());
        }
        String where = mapAttributes(expr);
        String sql = "UPDATE " + oTable + " SET " + String.join(", ", sets) +
                (where == null || where.isBlank() ? "" : " WHERE " + where);
        engine.executeUpdate(sql, values);
        return this;
    }

    public void addColumns(String fields) {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table");
        throw new UnsupportedOperationException("Ontology.addColumns 尚未实现");
    }

    public void dropTable() {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table");
        throw new UnsupportedOperationException("Ontology.dropTable 尚未实现");
    }

    /**
     * 属性名 -&gt; 物理列名映射（基于 @Column 注解字段的字段值）。
     * 例如 {@code toolCode -&gt; tool_code}。
     */
    protected Map<String, String> attributeColumnMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Field f : getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Column col = f.getAnnotation(Column.class);
            if (col == null) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(this);
                if (v instanceof String physical && !physical.isBlank()) {
                    map.put(f.getName(), physical);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return map;
    }

    /**
     * 将表达式中的本体属性名替换为物理列名。
     * 单引号字符串字面量内的内容原样保留（支持 '' 转义）；
     * 按属性名长度降序替换，避免前缀误伤；已是物理列名的保持不变。
     */
    protected String mapAttributes(String expr) {
        Map<String, String> attrMap = attributeColumnMap();
        if (expr == null || attrMap.isEmpty()) return expr;
        List<String> attrs = new ArrayList<>(attrMap.keySet());
        attrs.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder out = new StringBuilder();
        StringBuilder seg = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '\'') {
                if (inQuote && i + 1 < expr.length() && expr.charAt(i + 1) == '\'') {
                    seg.append("''");
                    i++;
                    continue;
                }
                if (inQuote) {
                    out.append('\'').append(seg).append('\'');
                } else {
                    out.append(replaceAttributes(seg.toString(), attrs, attrMap));
                }
                seg.setLength(0);
                inQuote = !inQuote;
            } else {
                seg.append(c);
            }
        }
        if (inQuote) {
            out.append('\'').append(seg);
        } else {
            out.append(replaceAttributes(seg.toString(), attrs, attrMap));
        }
        return out.toString();
    }

    private String replaceAttributes(String seg, List<String> attrs, Map<String, String> attrMap) {
        String r = seg;
        for (String attr : attrs) {
            String physical = attrMap.get(attr);
            if (physical.equals(attr)) continue;
            r = r.replaceAll("\\b" + java.util.regex.Pattern.quote(attr) + "\\b",
                    java.util.regex.Matcher.quoteReplacement(physical));
        }
        return r;
    }

    // Helper method
    protected CmdDataframe from(CmdDatasource ds, String table) {
        FromOperator op = new FromOperator(ds, table);
        return new CmdDataframeImpl(op, GdlExecutionContext.get().getExecutionEngine());
    }

    // Metaclass interception for areaCode drifting
    @Override
    public Object invokeMethod(String name, Object args) {
        boolean isOutermost = (callDepth == 0);
        if (isOutermost && !areaCodes.isEmpty()) {
            beginMethodCall(name, args);
        }
        callDepth++;
        try {
            return getMetaClass().invokeMethod(this, name, args);
        } finally {
            callDepth--;
            if (isOutermost && !areaCodes.isEmpty()) {
                endMethodCall(name);
            }
        }
    }

    protected void beginMethodCall(String methodName, Object args) {
        // Tagged for remote node dispatch if areaCode differs
    }

    protected void endMethodCall(String methodName) {
        // Untag
    }
}
