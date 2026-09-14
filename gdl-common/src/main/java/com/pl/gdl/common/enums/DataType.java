package com.pl.gdl.common.enums;

public enum DataType {
    VARCHAR("VARCHAR", "string", java.sql.Types.VARCHAR),
    INTEGER("INTEGER", "int", java.sql.Types.INTEGER),
    BIGINT("BIGINT", "bigint", java.sql.Types.BIGINT),
    DECIMAL("DECIMAL", "decimal", java.sql.Types.DECIMAL),
    DOUBLE("DOUBLE", "double", java.sql.Types.DOUBLE),
    BOOLEAN("BOOLEAN", "boolean", java.sql.Types.BOOLEAN),
    DATE("DATE", "date", java.sql.Types.DATE),
    TIMESTAMP("TIMESTAMP", "timestamp", java.sql.Types.TIMESTAMP);

    private final String standardName;
    private final String hiveType;
    private final int jdbcType;

    DataType(String standardName, String hiveType, int jdbcType) {
        this.standardName = standardName;
        this.hiveType = hiveType;
        this.jdbcType = jdbcType;
    }

    public String getStandardName() {
        return standardName;
    }

    public String getHiveType() {
        return hiveType;
    }

    public int getJdbcType() {
        return jdbcType;
    }

    public static DataType fromString(String name) {
        if (name == null || name.isBlank()) {
            return VARCHAR;
        }
        String upper = name.trim().toUpperCase();
        if (upper.startsWith("VARCHAR") || upper.equals("STRING") || upper.equals("TEXT")) {
            return VARCHAR;
        } else if (upper.equals("INT") || upper.equals("INTEGER")) {
            return INTEGER;
        } else if (upper.equals("BIGINT") || upper.equals("LONG")) {
            return BIGINT;
        } else if (upper.startsWith("DECIMAL") || upper.equals("NUMERIC")) {
            return DECIMAL;
        } else if (upper.equals("DOUBLE") || upper.equals("FLOAT")) {
            return DOUBLE;
        } else if (upper.equals("BOOLEAN") || upper.equals("BOOL")) {
            return BOOLEAN;
        } else if (upper.equals("DATE")) {
            return DATE;
        } else if (upper.equals("TIMESTAMP") || upper.equals("DATETIME")) {
            return TIMESTAMP;
        }
        return VARCHAR;
    }
}
