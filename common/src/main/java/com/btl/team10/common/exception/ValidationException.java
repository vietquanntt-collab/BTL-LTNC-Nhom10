package com.btl.team10.common.exception;

import com.btl.team10.common.protocol.ErrorCode;

/** Thrown when a shared contract value fails validation. */
public final class ValidationException extends IllegalArgumentException {
    private final String field;

    public ValidationException(String field, String message) {
        super(field + ": " + message);
        this.field = field;
    }

    public String field() {
        return field;
    }

    public ErrorCode errorCode() {
        return ErrorCode.VALIDATION_ERROR;
    }
}
