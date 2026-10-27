package com.btl.team10.common.protocol;

public record ResponseEnvelope(
        String protocolVersion,
        String requestId,
        String action,
        boolean ok,
        ErrorCode errorCode,
        String message,
        String sessionId,
        String payloadJson) {

    public static ResponseEnvelope success(
            String requestId, String action, String sessionId, String payloadJson) {
        return new ResponseEnvelope(
                "v1", requestId, action, true, ErrorCode.NONE, "OK", sessionId, payloadJson);
    }

    public static ResponseEnvelope failure(
            String requestId, String action, ErrorCode errorCode, String message) {
        return new ResponseEnvelope(
                "v1", requestId, action, false, errorCode, message, null, "{}");
    }
}
