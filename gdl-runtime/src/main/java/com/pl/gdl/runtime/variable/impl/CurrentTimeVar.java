package com.pl.gdl.runtime.variable.impl;

import com.pl.gdl.runtime.script.GdlExecutionContext;
import com.pl.gdl.runtime.variable.DynamicVariable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

public class CurrentTimeVar implements DynamicVariable {
    @Override
    public String getGeneratorName() {
        return "CurrentTimeVar";
    }

    @Override
    public Object resolve(GdlExecutionContext context, Map<String, Object> params) {
        String varName = params != null && params.containsKey("timeVarName") ? String.valueOf(params.get("timeVarName")) : "time";
        String format = params != null && params.containsKey("timeFormat") ? String.valueOf(params.get("timeFormat")) : null;

        Date now = new Date();
        String formattedValue;
        if (format != null && !format.isBlank()) {
            formattedValue = new SimpleDateFormat(format).format(now);
        } else {
            formattedValue = String.valueOf(now.getTime() / 1000);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(varName, formattedValue);
        result.put("DATE8", new SimpleDateFormat("yyyyMMdd").format(now));
        result.put("DATE10", new SimpleDateFormat("yyyyMMddHH").format(now));
        result.put("DATE14", new SimpleDateFormat("yyyyMMddHHmmss").format(now));
        result.put("DATE_8", new SimpleDateFormat("yyyy-MM-dd").format(now));
        result.put("DATE_14", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(now));
        result.put("SEC", String.valueOf(now.getTime() / 1000));
        return result;
    }
}
