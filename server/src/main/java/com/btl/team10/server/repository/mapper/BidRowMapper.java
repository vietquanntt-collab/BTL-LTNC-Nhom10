package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.model.BidStatus;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps a bids row to the shared bid DTO. */
public final class BidRowMapper {
    private BidRowMapper() {
    }

    public static BidDto map(ResultSet result) throws SQLException {
        return new BidDto(
                result.getLong("bid_id"),
                result.getLong("auction_id"),
                result.getLong("bidder_id"),
                result.getLong("amount_cents"),
                ResultSetValues.requiredInstant(result, "bid_time"),
                ResultSetValues.requiredEnum(result, "status", BidStatus.class),
                result.getString("client_request_id"));
    }
}
