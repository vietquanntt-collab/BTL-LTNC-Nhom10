package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.AuctionStatus;

import java.time.Instant;

public record AuctionDto(
        long auctionId,
        long itemId,
        long startPriceCents,
        long currentPriceCents,
        long minIncrementCents,
        Instant startAt,
        Instant endAt,
        AuctionStatus status,
        long version,
        int extensionCount,
        Long winnerId,
        Instant closedAt,
        Instant createdAt) {

    public AuctionDto {
        requirePositive(auctionId, "auctionId");
        requirePositive(itemId, "itemId");
        requireNonNegative(startPriceCents, "startPriceCents");
        if (currentPriceCents < startPriceCents) {
            throw new ValidationException("currentPriceCents", "must not be below startPriceCents");
        }
        if (minIncrementCents <= 0) {
            throw new ValidationException("minIncrementCents", "must be positive");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ValidationException("endAt", "must be after startAt");
        }
        if (status == null) {
            throw new ValidationException("status", "must not be null");
        }
        requireNonNegative(version, "version");
        if (extensionCount < 0) {
            throw new ValidationException("extensionCount", "must not be negative");
        }
        if (winnerId != null && winnerId <= 0) {
            throw new ValidationException("winnerId", "must be positive when present");
        }
        if (createdAt == null) {
            throw new ValidationException("createdAt", "must not be null");
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
