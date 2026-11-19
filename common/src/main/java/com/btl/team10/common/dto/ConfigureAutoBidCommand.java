package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

/** Input for creating or replacing one bidder/auction auto-bid rule. */
public record ConfigureAutoBidCommand(
        long auctionId,
        long bidderId,
        long maxAmountCents,
        long incrementCents) {

    public ConfigureAutoBidCommand {
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        requirePositive(maxAmountCents, "maxAmountCents");
        requirePositive(incrementCents, "incrementCents");
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
