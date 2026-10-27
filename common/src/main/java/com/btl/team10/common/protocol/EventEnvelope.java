package com.btl.team10.common.protocol;

import java.time.Instant;

public record EventEnvelope(
        String protocolVersion,
        String eventId,
        EventType eventType,
        long auctionId,
        long sequence,
        Instant occurredAt,
        String payloadJson) {

    public EventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (auctionId < 0 || sequence < 1) {
            throw new IllegalArgumentException("auctionId/sequence is invalid");
        }
        if (eventType == null || occurredAt == null || payloadJson == null) {
            throw new IllegalArgumentException("event metadata must not be null");
        }
    }
}
