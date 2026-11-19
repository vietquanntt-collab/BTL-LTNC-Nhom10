package com.btl.team10.common;

import com.btl.team10.common.dto.AutoBidRuleDto;
import com.btl.team10.common.dto.ConfigureAutoBidCommand;
import com.btl.team10.common.dto.SettleAuctionCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Week4ContractTest {

    @Test
    void autoBidCommandRejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigureAutoBidCommand(1, 2, 0, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigureAutoBidCommand(1, 2, 100, 0));
    }

    @Test
    void settlementWithoutWinnerMustHaveZeroAmountAndRequestId() {
        SettleAuctionCommand command = new SettleAuctionCommand(1, 2, null, 0, "settle-1");
        assertEquals("settle-1", command.requestId());
        assertThrows(IllegalArgumentException.class,
                () -> new SettleAuctionCommand(1, 2, null, 100, "settle-2"));
        assertThrows(IllegalArgumentException.class,
                () -> new SettleAuctionCommand(1, 2, null, 0, " "));
    }

    @Test
    void autoBidRuleRoundTripsThroughValidation() {
        AutoBidRuleDto rule = new AutoBidRuleDto(
                1, 2, 3, 10_000, 500, Instant.parse("2026-11-20T00:00:00Z"), true);
        assertEquals(500, rule.incrementCents());
        assertEquals(true, rule.active());
    }
}
