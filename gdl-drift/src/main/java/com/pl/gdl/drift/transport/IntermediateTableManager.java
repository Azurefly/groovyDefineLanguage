package com.pl.gdl.drift.transport;

import com.pl.gdl.common.constant.GdlConstants;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
