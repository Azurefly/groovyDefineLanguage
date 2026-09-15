package com.pl.gdl.runtime.federation;

import com.pl.gdl.runtime.dag.DagNode;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * Physical execution domain for one DAG fragment. A boundary exists when
 * either the area code or physical datasource identity changes.
 */
public record ExecutionDomain(
        String areaCode,
        String datasourceType,
        String datasourceName
) implements Serializable {

    public ExecutionDomain {
        areaCode = normalize(areaCode, "local");
        datasourceType = normalize(datasourceType, "LOCAL_ENGINE");
        datasourceName = normalizeIdentity(datasourceName, "default");
    }

    public static ExecutionDomain fromNode(DagNode node) {
        Objects.requireNonNull(node, "node must not be null");
        Object type = node.getProperties().get("datasourceType");
        Object identity = node.getProperties().get("datasourceIdentity");
        Object name = identity != null ? identity : node.getProperties().get("datasourceName");
        return new ExecutionDomain(
                node.getAreaCode(),
                type == null ? null : String.valueOf(type),
                name == null ? null : String.valueOf(name));
    }

    public String key() {
        return areaCode + ":" + datasourceType + ":" + datasourceName;
    }

    private static String normalize(String value, String fallback) {
        String effective = value == null || value.isBlank() ? fallback : value.trim();
        return effective.toUpperCase(Locale.ROOT);
    }

    private static String normalizeIdentity(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
