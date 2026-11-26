package com.btl.team10.common;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.serialization.JsonCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {

    @Test
    void roundTripsDtoEnumsInstantsAndNullableFields() {
        Instant now = Instant.parse("2026-11-06T12:34:56Z");
        AuctionDto original = new AuctionDto(
                7, 12, 1000, 1000, 50,
                now, now.plusSeconds(3600), AuctionStatus.OPEN,
                0, 0, null, null, now);

        String json = JsonCodec.toJson(original);
        AuctionDto decoded = JsonCodec.fromJson(json, AuctionDto.class);

        assertTrue(json.contains("\"winnerId\":null"));
        assertEquals(original, decoded);
    }

    @Test
    void rejectsBlankJson() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.google.gson.JsonParseException.class,
                () -> JsonCodec.fromJson("  ", AuctionDto.class));
    }
}
