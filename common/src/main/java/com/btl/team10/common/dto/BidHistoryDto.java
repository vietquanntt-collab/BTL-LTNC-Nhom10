package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.BidStatus;

import java.time.Instant;

/** Immutable audit record for an accepted bid event. */
public record BidHistoryDto(
        long historyId,
        long bidId,
        long auctionId,
        long bidderId,
        long amountCents,
        Instant bidTime,
        BidStatus status,
        String clientRequestId,
        long auctionVersion,
        Instant recordedAt) {

    public BidHistoryDto {
        requirePositive(historyId, "historyId");
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
        clientRequestId = clientRequestId.trim();
        if (auctionVersion <= 0) {
            throw new ValidationException("auctionVersion", "must be positive");
        }
        if (recordedAt == null) {
            throw new ValidationException("recordedAt", "must not be null");
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
