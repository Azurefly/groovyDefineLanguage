package com.pl.gdl.common.enums;

public enum TableType {
    ORC,
    FRC,
    MAGICTABLE,
    RDBMS;

    public static TableType fromString(String str) {
        if (str == null) return ORC;
        try {
            return valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ORC;
        }
    }
}
