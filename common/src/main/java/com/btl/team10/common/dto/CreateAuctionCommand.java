package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

import java.time.Instant;

public record CreateAuctionCommand(
        long itemId,
        long startPriceCents,
        long minIncrementCents,
        Instant startAt,
        Instant endAt) {

    public CreateAuctionCommand {
        if (itemId <= 0) {
            throw new ValidationException("itemId", "must be positive");
        }
        if (startPriceCents < 0) {
            throw new ValidationException("startPriceCents", "must not be negative");
        }
        if (minIncrementCents <= 0) {
            throw new ValidationException("minIncrementCents", "must be positive");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new ValidationException("endAt", "must be after startAt");
        }
    }
}
