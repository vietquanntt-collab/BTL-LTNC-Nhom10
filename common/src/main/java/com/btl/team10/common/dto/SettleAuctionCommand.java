package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

/** Idempotent settlement request persisted by the data layer. */
public record SettleAuctionCommand(
        long auctionId,
        long sellerId,
        Long winnerId,
        long winningAmountCents,
        String requestId) {

    public SettleAuctionCommand {
        requirePositive(auctionId, "auctionId");
        requirePositive(sellerId, "sellerId");
        if (winnerId != null && winnerId <= 0) {
            throw new ValidationException("winnerId", "must be positive when present");
        }
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
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
