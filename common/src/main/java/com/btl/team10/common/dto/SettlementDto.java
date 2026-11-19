package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

import java.time.Instant;

/** Immutable settlement record; one auction can have at most one record. */
public record SettlementDto(
        long settlementId,
        long auctionId,
        Long winnerId,
        long sellerId,
        long winningAmountCents,
        String requestId,
        Instant settledAt) {

    public SettlementDto {
        requirePositive(settlementId, "settlementId");
        requirePositive(auctionId, "auctionId");
        if (winnerId != null && winnerId <= 0) {
            throw new ValidationException("winnerId", "must be positive when present");
        }
        requirePositive(sellerId, "sellerId");
        if (winningAmountCents < 0) {
            throw new ValidationException("winningAmountCents", "must not be negative");
        }
        if (winnerId == null && winningAmountCents != 0) {
            throw new ValidationException(
                    "winningAmountCents", "must be zero when the auction has no winner");
        }
        if (winnerId != null && winningAmountCents <= 0) {
            throw new ValidationException(
                    "winningAmountCents", "must be positive when a winner is present");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new ValidationException("requestId", "must not be blank");
        }
        if (settledAt == null) {
            throw new ValidationException("settledAt", "must not be null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
