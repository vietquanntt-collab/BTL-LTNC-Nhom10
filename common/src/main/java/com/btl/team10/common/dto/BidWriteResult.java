package com.btl.team10.common.dto;

import com.btl.team10.common.exception.ValidationException;

/** Result returned after an atomic bid write and auction version update. */
public record BidWriteResult(
        BidDto bid,
        long auctionVersion,
        long currentPriceCents) {

    public BidWriteResult {
        if (bid == null) {
            throw new ValidationException("bid", "must not be null");
        }
        if (auctionVersion <= 0) {
            throw new ValidationException("auctionVersion", "must be positive");
        }
        if (currentPriceCents < bid.amountCents()) {
            throw new ValidationException("currentPriceCents", "must not be below bid amount");
        }
    }
}
