package com.pl.gdl.runtime.variable;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import java.util.Map;

public interface DynamicVariable {
    String getGeneratorName();
    Object resolve(GdlExecutionContext context, Map<String, Object> params);
}
