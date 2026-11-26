package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

/** Wallet snapshot used by server policies; money is stored in integer cents. */
public record WalletDto(
        long userId,
        long balanceCents,
        long frozenCents,
        long version) {

    public WalletDto {
        requirePositive(userId, "userId");
        requireNonNegative(balanceCents, "balanceCents");
        requireNonNegative(frozenCents, "frozenCents");
        if (frozenCents > balanceCents) {
            throw new ValidationException("frozenCents", "must not exceed balanceCents");
        }
        requireNonNegative(version, "version");
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
