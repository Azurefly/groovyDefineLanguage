package com.pl.gdl.drift.transport;

import com.pl.gdl.common.constant.GdlConstants;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中间表名称管理器（单例）。
 *
 * <p><b>职责边界（诚实说明）：</b>本类仅做进程内的中间表<b>名称登记</b>
 * （生成唯一表名、记录存活集合、释放登记），<b>不持有任何 JDBC 连接</b>，
 * 也不实际创建 / 删除物理表。物理中间表的建表与 DROP 由调用方
 * （执行引擎 / 传输实现）负责；{@link #release(String)} 只是把表名从
 * 存活集合中移除，不会执行任何 DROP 语句。</p>
 */
public class IntermediateTableManager {
    private static final IntermediateTableManager INSTANCE = new IntermediateTableManager();
    private final Set<String> activeTempTables = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private IntermediateTableManager() {}

    public static IntermediateTableManager getInstance() {
        return INSTANCE;
    }

    public String generateTempTableName() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long ts = System.currentTimeMillis();
        String name = GdlConstants.TEMP_TABLE_PREFIX + uuid + "_" + ts;
        activeTempTables.add(name);
        return name;
    }

    public void register(String tableName) {
        if (tableName != null) {
            activeTempTables.add(tableName);
        }
    }

    public void release(String tableName) {
        if (tableName != null) {
            activeTempTables.remove(tableName);
        }
    }

    public void releaseAll() {
        activeTempTables.clear();
    }

    public Set<String> getActiveTempTables() {
        return Collections.unmodifiableSet(activeTempTables);
    }
}
