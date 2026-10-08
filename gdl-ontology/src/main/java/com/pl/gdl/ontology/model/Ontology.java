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
    /** 累计的 where 条件（已映射为物理列名），供 count() 复用 */
    private final List<String> whereConditions = new ArrayList<>();
    /** 表列名缓存（小写），用于审计字段/乐观锁的列存在性判断 */
    private volatile java.util.Set<String> tableColumnsCache = null;
    /** 生命周期钩子：事件名 -> 闭包列表（beforeSave/afterSave/beforeUpdate/afterUpdate/beforeDelete/afterDelete） */
    private final Map<String, List<groovy.lang.Closure<?>>> hooks = new LinkedHashMap<>();
    /** 软删除列名（约定：物理表有 deleted 列即启用），null 表示未启用；includeDeleted 为 true 时查询不过滤 */
    private volatile String softDeleteColumn = null;
    private volatile boolean softDeleteResolved = false;
    private volatile boolean includeDeletedFlag = false;
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
            // 软删除：默认过滤 deleted=1 的行；includeDeleted() 可关闭
            String sdCol = softDeleteColumn();
            if (sdCol != null && !includeDeletedFlag) {
                oDataframe = oDataframe.where(sdCol + " = 0");
            }
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
            CmdDataframe df = from(oDs, oTable);
            // 软删除：默认过滤 deleted=1 的行；includeDeleted() 可关闭
            String sdCol = softDeleteColumn();
            if (sdCol != null && !includeDeletedFlag) {
                df = df.where(sdCol + " = 0");
            }
            return df;
        }
        return null;
    }

    // ---------------- 生命周期钩子 ----------------
    // 用闭包注册（而非方法定义），避开沙箱方法定义的类加载问题。
    // before* 钩子返回 Boolean.FALSE 即否决本次写操作，抛 HookVetoException；
    // before* 收到可变的 record，可直接修改（如 beforeSave 里算金额）。
    /** 通用钩子注册：event 取 beforeSave/afterSave/beforeUpdate/afterUpdate/beforeDelete/afterDelete */
    public Ontology hook(String event, groovy.lang.Closure<?> hook) {
        if (event == null || event.isBlank()) throw new IllegalArgumentException("hook event 不能为空");
        if (hook == null) throw new IllegalArgumentException("hook 闭包不能为空");
        hooks.computeIfAbsent(event, k -> new ArrayList<>()).add(hook);
        return this;
    }

    public Ontology beforeSave(groovy.lang.Closure<?> hook) { return hook("beforeSave", hook); }
    public Ontology afterSave(groovy.lang.Closure<?> hook) { return hook("afterSave", hook); }
    public Ontology beforeUpdate(groovy.lang.Closure<?> hook) { return hook("beforeUpdate", hook); }
    public Ontology afterUpdate(groovy.lang.Closure<?> hook) { return hook("afterUpdate", hook); }
    public Ontology beforeDelete(groovy.lang.Closure<?> hook) { return hook("beforeDelete", hook); }
    public Ontology afterDelete(groovy.lang.Closure<?> hook) { return hook("afterDelete", hook); }

    /** 触发 before* 钩子；任一返回 Boolean.FALSE 则抛 HookVetoException */
    private void fireBefore(String event, Object... args) {
        List<groovy.lang.Closure<?>> list = hooks.get(event);
        if (list == null) return;
        for (groovy.lang.Closure<?> h : list) {
            Object r = h.call(args);
            if (r instanceof Boolean && !((Boolean) r)) {
                throw new com.pl.gdl.common.exception.HookVetoException(
                        "钩子否决写操作：" + event + "（" + oTable + "）");
            }
        }
    }

    /** 触发 after* 钩子（异常直接传播，事务内调用可触发回滚） */
    private void fireAfter(String event, Object... args) {
        List<groovy.lang.Closure<?>> list = hooks.get(event);
        if (list == null) return;
        for (groovy.lang.Closure<?> h : list) {
            h.call(args);
        }
    }

    // ---------------- 软删除 ----------------
    // 约定优于配置：物理表有 deleted 列（0=正常/1=已删）即启用软删除。
    // delete() 改为 UPDATE SET deleted=1；查询链/count() 默认过滤已删行；forceDelete() 仍物理删除。
    /** 解析软删除列；非 JDBC/元数据失败时降级为不启用（不影响纯查询） */
    private String softDeleteColumn() {
        if (!softDeleteResolved) {
            String col = null;
            try {
                if (tableColumns().contains("deleted")) col = "deleted";
            } catch (Exception ignored) {
            }
            softDeleteColumn = col;
            softDeleteResolved = true;
        }
        return softDeleteColumn;
    }

    /** 查询包含已删除行（需在构建查询链之前调用，会重置当前查询链） */
    public Ontology includeDeleted() {
        includeDeletedFlag = true;
        queryDataframe = null;
        oDataframe = null;
        return this;
    }

    /** expr 是否已显式引用 deleted 列（用户显式写了就尊重，不再自动追加条件） */
    private boolean exprMentionsDeleted(String expr) {
        return expr != null && expr.toLowerCase().contains("deleted");
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
        String mapped = mapAttributes(condition);
        if (queryDataframe != null) {
            queryDataframe = queryDataframe.where(mapped);
        }
        if (mapped != null && !mapped.isBlank()) {
            whereConditions.add(mapped);
        }
        return this;
    }

    /**
     * 按当前 where 条件统计总数。用于分页 UI 的"共 N 条"。
     * 例：{@code long total = tool.where("category='铣刀'").count();}
     */
    public long count() {
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        String where = String.join(" AND ", whereConditions);
        // 软删除：默认只统计未删除行
        String sdCol = softDeleteColumn();
        if (sdCol != null && !includeDeletedFlag && !exprMentionsDeleted(where)) {
            where = where.isEmpty() ? sdCol + " = 0" : where + " AND " + sdCol + " = 0";
        }
        String sql = "SELECT COUNT(*) FROM " + oTable + (where.isEmpty() ? "" : " WHERE " + where);
        final long[] result = new long[1];
        // 复用 executeUpdate 的连接逻辑，执行查询
        try {
            java.sql.Connection conn = engine.getDatasource() != null
                    ? com.pl.gdl.dataframe.engine.JdbcConnectionManager.getDefault()
                            .getConnection((com.pl.gdl.dataframe.datasource.JdbcDatasource) oDs)
                    : null;
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) result[0] = rs.getLong(1);
            } finally {
                if (conn != null) conn.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("count failed: " + e.getMessage() + " | SQL: " + sql, e);
        }
        return result[0];
    }

    /** 获取物理表列名集合（小写缓存） */
    private java.util.Set<String> tableColumns() {
        if (tableColumnsCache != null) return tableColumnsCache;
        java.util.Set<String> cols = new java.util.HashSet<>();
        try {
            java.sql.Connection conn = com.pl.gdl.dataframe.engine.JdbcConnectionManager.getDefault()
                    .getConnection((com.pl.gdl.dataframe.datasource.JdbcDatasource) oDs);
            try {
                java.sql.DatabaseMetaData meta = conn.getMetaData();
                // H2 等库 unquoted 标识符存大写，两种 case 都试
                for (String tn : new String[]{oTable, oTable.toUpperCase(), oTable.toLowerCase()}) {
                    try (java.sql.ResultSet rs = meta.getColumns(conn.getCatalog(), null, tn, "%")) {
                        while (rs.next()) {
                            cols.add(rs.getString("COLUMN_NAME").toLowerCase());
                        }
                    }
                    if (!cols.isEmpty()) break;
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("get table columns failed: " + e.getMessage(), e);
        }
        tableColumnsCache = cols;
        return cols;
    }

    /** 审计字段自动填充（若表有这些列且 record 未提供） */
    private void fillAuditFields(Map<String, Object> record, boolean isInsert) {
        java.util.Set<String> cols = tableColumns();
        Map<String, String> attrMap = attributeColumnMap();
        // 反向映射：物理列 -> 属性名（用于检查 record 是否已提供）
        java.util.Set<String> recordKeysLower = new java.util.HashSet<>();
        for (String k : record.keySet()) {
            recordKeysLower.add(k.toLowerCase());
            String phys = attrMap.get(k);
            if (phys != null) recordKeysLower.add(phys.toLowerCase());
        }
        long now = System.currentTimeMillis();
        String operator = oAuthor != null ? oAuthor : "system";
        // 列名约定：created_by/created_time/updated_by/updated_time（大小写不敏感）
        if (isInsert) {
            if (cols.contains("created_by") && !recordKeysLower.contains("created_by")) {
                record.put("created_by", operator);
            }
            if (cols.contains("created_time") && !recordKeysLower.contains("created_time")) {
                record.put("created_time", new java.sql.Timestamp(now));
            }
        }
        if (cols.contains("updated_by") && !recordKeysLower.contains("updated_by")) {
            record.put("updated_by", operator);
        }
        if (cols.contains("updated_time") && !recordKeysLower.contains("updated_time")) {
            record.put("updated_time", new java.sql.Timestamp(now));
        }
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
        fillAuditFields(record, true);
        fillSoftDeleteDefault(record);
        fireBefore("beforeSave", record);
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
        fireAfter("afterSave", record);
        return this;
    }

    public Ontology delete(String expr) {
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        String where = mapAttributes(expr);
        String sdCol = softDeleteColumn();
        int affected;
        if (sdCol != null) {
            // 软删除：UPDATE 置 deleted=1（幂等，已删除的行不再重复删），同时刷审计字段/version
            fireBefore("beforeDelete", expr);
            List<String> sets = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            sets.add(sdCol + " = 1");
            java.util.Set<String> cols = tableColumns();
            if (cols.contains("updated_by")) {
                sets.add("updated_by = ?");
                params.add(oAuthor != null ? oAuthor : "system");
            }
            if (cols.contains("updated_time")) {
                sets.add("updated_time = ?");
                params.add(new java.sql.Timestamp(System.currentTimeMillis()));
            }
            if (cols.contains("version")) sets.add("version = version + 1");
            StringBuilder sql = new StringBuilder("UPDATE " + oTable + " SET " + String.join(", ", sets));
            if (where != null && !where.isBlank()) {
                sql.append(" WHERE ").append(where);
                if (!exprMentionsDeleted(where)) sql.append(" AND ").append(sdCol).append(" = 0");
            } else if (!exprMentionsDeleted(where)) {
                sql.append(" WHERE ").append(sdCol).append(" = 0");
            }
            affected = engine.executeUpdate(sql.toString(), params);
        } else {
            // 无 deleted 列：保持物理删除（向后兼容）
            fireBefore("beforeDelete", expr);
            String sql = "DELETE FROM " + oTable + (where == null || where.isBlank() ? "" : " WHERE " + where);
            affected = engine.executeUpdate(sql, Collections.emptyList());
        }
        fireAfter("afterDelete", expr, affected);
        return this;
    }

    /** 物理删除（无视软删除约定，直接 DELETE；审计留痕请用 delete()） */
    public Ontology forceDelete(String expr) {
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        fireBefore("beforeDelete", expr);
        String where = mapAttributes(expr);
        String sql = "DELETE FROM " + oTable + (where == null || where.isBlank() ? "" : " WHERE " + where);
        int affected = engine.executeUpdate(sql, Collections.emptyList());
        fireAfter("afterDelete", expr, affected);
        return this;
    }

    /** 软删除 insert 时默认 deleted=0（表有该列且 record 未提供时） */
    private void fillSoftDeleteDefault(Map<String, Object> record) {
        if (softDeleteColumn() == null) return;
        boolean provided = false;
        for (String k : record.keySet()) {
            if (k.equalsIgnoreCase("deleted")) { provided = true; break; }
        }
        if (!provided) record.put("deleted", 0);
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
        for (Map<String, Object> r : records) {
            fillAuditFields(r, true);
            fillSoftDeleteDefault(r);
            fireBefore("beforeSave", r);
        }
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
        int[] affected = engine.executeBatch(sql, batchParams);
        for (Map<String, Object> r : records) {
            fireAfter("afterSave", r);
        }
        return affected;
    }

    public Ontology update(String expr, Map<String, Object> record) {
        if (record == null || record.isEmpty()) throw new IllegalArgumentException("update record 不能为空");
        com.pl.gdl.dataframe.engine.JdbcExecutionEngine engine = writeEngine();
        fillAuditFields(record, false);
        fireBefore("beforeUpdate", expr, record);
        Map<String, String> attrMap = attributeColumnMap();
        List<String> sets = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        // 乐观锁：若 record 含 version（属性名或物理列名），则 SET version=version+1 并用旧版本做 WHERE
        Object oldVersion = null;
        String versionCol = null;
        for (Map.Entry<String, Object> e : record.entrySet()) {
            String phys = attrMap.getOrDefault(e.getKey(), e.getKey());
            if (phys.equalsIgnoreCase("version")) {
                oldVersion = e.getValue();
                versionCol = phys;
                continue;
            }
            sets.add(phys + " = ?");
            values.add(e.getValue());
        }
        if (versionCol != null) {
            sets.add(versionCol + " = " + versionCol + " + 1");
        }
        String where = mapAttributes(expr);
        StringBuilder sql = new StringBuilder("UPDATE " + oTable + " SET " + String.join(", ", sets));
        List<Object> whereValues = new ArrayList<>();
        // 软删除：默认不更新已删除行（expr 显式引用 deleted 时尊重用户，如恢复 deleted=0）
        String sdCol = softDeleteColumn();
        boolean needSdFilter = sdCol != null && !exprMentionsDeleted(where);
        if ((where != null && !where.isBlank()) || versionCol != null || needSdFilter) {
            sql.append(" WHERE ");
            boolean needAnd = false;
            if (where != null && !where.isBlank()) {
                sql.append(where);
                needAnd = true;
            }
            if (versionCol != null) {
                if (needAnd) sql.append(" AND ");
                sql.append(versionCol).append(" = ?");
                whereValues.add(oldVersion);
                needAnd = true;
            }
            if (needSdFilter) {
                if (needAnd) sql.append(" AND ");
                sql.append(sdCol).append(" = 0");
            }
        }
        values.addAll(whereValues);
        int affected = engine.executeUpdate(sql.toString(), values);
        if (versionCol != null && affected == 0) {
            throw new com.pl.gdl.common.exception.OptimisticLockException(
                    "乐观锁冲突：" + oTable + " 数据已被其他事务修改（version 不匹配）");
        }
        fireAfter("afterUpdate", expr, record, affected);
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
