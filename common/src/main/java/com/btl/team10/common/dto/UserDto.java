package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.Role;

import java.time.Instant;

public record UserDto(
        long userId,
        String account,
        Role role,
        long balanceCents,
        long frozenCents,
        Instant createdAt,
        Instant updatedAt) {

    public UserDto {
        requirePositive(userId, "userId");
        requireNonBlank(account, "account");
        if (role == null) {
            throw new ValidationException("role", "must not be null");
        }
        requireNonNegative(balanceCents, "balanceCents");
        requireNonNegative(frozenCents, "frozenCents");
        if (frozenCents > balanceCents) {
            throw new ValidationException("frozenCents", "must not exceed balanceCents");
        }
        if (createdAt == null || updatedAt == null) {
            throw new ValidationException("timestamps", "must not be null");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "must not be blank");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new ValidationException(field, "must not be negative");
        }
    }
}
