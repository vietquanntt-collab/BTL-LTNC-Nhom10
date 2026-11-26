package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

/** Validated input shared by the place-bid service and bid repository. */
public record PlaceBidCommand(
        long auctionId,
        long bidderId,
        long amountCents,
        String clientRequestId,
        long expectedAuctionVersion) {

    public PlaceBidCommand {
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        if (amountCents <= 0) {
            throw new ValidationException("amountCents", "must be positive");
        }
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new ValidationException("clientRequestId", "must not be blank");
        }
        clientRequestId = clientRequestId.trim();
        if (expectedAuctionVersion < 0) {
            throw new ValidationException("expectedAuctionVersion", "must not be negative");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
