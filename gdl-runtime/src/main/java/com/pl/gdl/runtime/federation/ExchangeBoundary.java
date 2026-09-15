package com.pl.gdl.runtime.federation;

import java.io.Serializable;

public record ExchangeBoundary(
        String id,
        String sourceNodeId,
        String targetNodeId,
        String sourceFragmentId,
        String targetFragmentId,
        ExecutionDomain sourceDomain,
        ExecutionDomain targetDomain,
        MaterializationMode mode
) implements Serializable {
    public enum MaterializationMode {
        MEMORY,
        STREAMING,
        INTERMEDIATE_TABLE,
        REMOTE_DRIFT
    }
}
