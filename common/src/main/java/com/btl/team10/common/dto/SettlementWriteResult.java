package com.btl.team10.common.dto;

/** Result of a settlement write; {@code created=false} means an idempotent retry. */
public record SettlementWriteResult(SettlementDto settlement, boolean created) {
    public SettlementWriteResult {
        if (settlement == null) {
            throw new IllegalArgumentException("settlement must not be null");
        }
    }
}
