package com.btl.team10.common.protocol;

import java.util.Objects;

public record RequestEnvelope(
        String protocolVersion,
        String requestId,
        String action,
        String sessionId,
        String payloadJson) {

    public RequestEnvelope {
        requireNonBlank(protocolVersion, "protocolVersion");
        requireNonBlank(requestId, "requestId");
        requireNonBlank(action, "action");
        requireNonBlank(payloadJson, "payloadJson");
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
