package com.pl.gdl.common.enums;

public enum WindowUnit {
    SECONDS,
    MINUTES,
    HOURS,
    DAYS;

    public static WindowUnit fromString(String str) {
        if (str == null) return MINUTES;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MINUTES;
        }
    }
}
