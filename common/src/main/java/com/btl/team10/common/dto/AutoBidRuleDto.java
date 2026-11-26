package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

import java.time.Instant;

/** Shared representation of one bidder's automatic bidding rule. */
public record AutoBidRuleDto(
        long ruleId,
        long auctionId,
        long bidderId,
        long maxAmountCents,
        long incrementCents,
        Instant registeredAt,
        boolean active) {

    public AutoBidRuleDto {
        requirePositive(ruleId, "ruleId");
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        requirePositive(maxAmountCents, "maxAmountCents");
        requirePositive(incrementCents, "incrementCents");
        if (incrementCents > maxAmountCents) {
            throw new ValidationException("incrementCents", "must not exceed maxAmountCents");
        }
        if (registeredAt == null) {
            throw new ValidationException("registeredAt", "must not be null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
