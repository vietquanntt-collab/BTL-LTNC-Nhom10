package com.btl.team10.common;

import com.btl.team10.common.dto.BidHistoryDto;
import com.btl.team10.common.dto.BidWriteResult;
import com.btl.team10.common.dto.PlaceBidCommand;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.BidStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BidContractTest {
    private static final Instant BID_TIME = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void placeBidCommandNormalizesRequestIdAndAcceptsVersionZero() {
        PlaceBidCommand command = new PlaceBidCommand(1, 2, 1_050, "  req-1  ", 0);

        assertEquals("req-1", command.clientRequestId());
        assertEquals(0, command.expectedAuctionVersion());
    }

    @Test
    void placeBidCommandRejectsInvalidAmountAndRequestId() {
        assertThrows(ValidationException.class,
                () -> new PlaceBidCommand(1, 2, 0, "req-1", 0));
        assertThrows(ValidationException.class,
                () -> new PlaceBidCommand(1, 2, 100, " ", 0));
    }

    @Test
    void bidHistoryRequiresPositiveCommittedVersion() {
        assertThrows(ValidationException.class, () -> new BidHistoryDto(
                1, 2, 3, 4, 1_000, BID_TIME, BidStatus.ACTIVE, "req-1", 0, BID_TIME));
    }

    @Test
    void bidWriteResultKeepsPriceAtLeastBidAmount() {
        var bid = new com.btl.team10.common.dto.BidDto(
                2, 3, 4, 1_000, BID_TIME, BidStatus.ACTIVE, "req-1");
        assertThrows(ValidationException.class, () -> new BidWriteResult(bid, 1, 999));
    }
}
