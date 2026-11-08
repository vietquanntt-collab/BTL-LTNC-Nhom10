package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.BidStatus;

import java.time.Instant;

public record BidDto(
        long bidId,
        long auctionId,
        long bidderId,
        long amountCents,
        Instant bidTime,
        BidStatus status,
        String clientRequestId) {

    public BidDto {
        requirePositive(bidId, "bidId");
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        if (amountCents <= 0) {
            throw new ValidationException("amountCents", "must be positive");
        }
        if (bidTime == null) {
            throw new ValidationException("bidTime", "must not be null");
        }
        if (status == null) {
            throw new ValidationException("status", "must not be null");
        }
        if (clientRequestId == null || clientRequestId.isBlank()) {
            throw new ValidationException("clientRequestId", "must not be blank");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
