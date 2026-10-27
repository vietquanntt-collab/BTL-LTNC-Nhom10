package com.btl.team10.common;

import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolContractTest {

    @Test
    void enumsExposeOnlyProtocolV1Values() {
        assertEquals(
                "OPEN,RUNNING,FINISHED,PAID,CANCELLED",
                String.join(",", java.util.Arrays.stream(AuctionStatus.values()).map(Enum::name).toList()));
        assertEquals(
                "ACTIVE,OUTBID,WINNER,REJECTED",
                String.join(",", java.util.Arrays.stream(BidStatus.values()).map(Enum::name).toList()));
    }

    @Test
    void requestRequiresStableIdentityAndAction() {
        RequestEnvelope request = new RequestEnvelope("v1", "r-1", "LIST_AUCTIONS", null, "{}");
        assertEquals("r-1", request.requestId());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RequestEnvelope("v1", "", "LIST_AUCTIONS", null, "{}"));
    }

    @Test
    void responseFactoryUsesNoErrorForSuccess() {
        ResponseEnvelope response = ResponseEnvelope.success("r-1", "LOGIN", "s-1", "{}");
        assertEquals("v1", response.protocolVersion());
        assertEquals(ErrorCode.NONE, response.errorCode());
        assertEquals(true, response.ok());
    }

    @Test
    void eventRequiresPositiveSequence() {
        EventEnvelope event = new EventEnvelope(
                "v1", "e-1", EventType.BID_ACCEPTED, 10, 1, Instant.parse("2026-10-22T00:00:00Z"), "{}");
        assertEquals(1, event.sequence());
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventEnvelope(
                        "v1", "e-2", EventType.BID_ACCEPTED, 10, 0,
                        Instant.now(), "{}"));
    }
}
