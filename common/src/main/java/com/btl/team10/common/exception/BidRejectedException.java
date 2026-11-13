package com.btl.team10.common.exception;

import com.btl.team10.common.protocol.ErrorCode;

import java.util.Objects;

/** Business rejection that the socket/service layer can map to a protocol error. */
public final class BidRejectedException extends IllegalStateException {
    private final ErrorCode errorCode;

    public BidRejectedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
