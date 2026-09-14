package com.pl.gdl.common.enums;

public enum DriftType {
    TABLE("table"),
    BIT("bit"),
    FRC("frc");

    private final String code;

    DriftType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static DriftType fromString(String code) {
        if (code == null) return TABLE;
        for (DriftType dt : values()) {
            if (dt.code.equalsIgnoreCase(code) || dt.name().equalsIgnoreCase(code)) {
                return dt;
            }
        }
        return TABLE;
    }
}
