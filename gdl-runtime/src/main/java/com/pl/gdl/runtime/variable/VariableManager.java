package com.pl.gdl.runtime.variable;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.impl.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VariableManager {
    private static final VariableManager INSTANCE = new VariableManager();
    private final Map<String, DynamicVariable> generators = new ConcurrentHashMap<>();

    private VariableManager() {
        register(new CurrentTimeVar());
        register(new CommonIncrementVar());
        register(new BdosPartitionIncrementVar());
        register(new BdosPartitionModifyIncrementVar());
        register(new BdosReadLastNPartitionVar());
        register(new BdosReadLastOnePartitionVar());
        register(new BdosReadLastPartitionVar());
    }

    public static VariableManager getInstance() {
        return INSTANCE;
    }

    public void register(DynamicVariable variable) {
        if (variable != null && variable.getGeneratorName() != null) {
            generators.put(variable.getGeneratorName().toLowerCase(), variable);
        }
    }

    public Object resolve(GdlExecutionContext context, String generatorName, Map<String, Object> params) {
        if (generatorName == null) return null;
        DynamicVariable gen = generators.get(generatorName.toLowerCase());
        if (gen == null) {
            throw new IllegalArgumentException("Unknown variable generator: " + generatorName);
        }
        return gen.resolve(context, params);
    }
}
