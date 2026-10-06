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

    // Storage mutations
    // 注意：以下写操作方法当前尚未实现。此前为静默 no-op（仅做空表检查后返回），调用方会误以为写入成功。
    // 现改为明确抛 UnsupportedOperationException，避免静默的数据丢失假象。
    public Ontology save(Map<String, Object> record) {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table, cannot save");
        throw new UnsupportedOperationException("Ontology.save 尚未实现：本体写回需要存储引擎对接");
    }

    public Ontology delete(String expr) {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table, cannot delete");
        throw new UnsupportedOperationException("Ontology.delete 尚未实现");
    }

    public Ontology update(String expr, Map<String, Object> record) {
        if (oTable == null) throw new RuntimeException("Ontology has no physical table, cannot update");
        throw new UnsupportedOperationException("Ontology.update 尚未实现");
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
