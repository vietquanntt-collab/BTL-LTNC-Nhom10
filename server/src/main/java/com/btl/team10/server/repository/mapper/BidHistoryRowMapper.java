package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.BidHistoryDto;
import com.btl.team10.common.model.BidStatus;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps an append-only bid_history row to its shared DTO. */
public final class BidHistoryRowMapper {
    private BidHistoryRowMapper() {
    }

    public static BidHistoryDto map(ResultSet result) throws SQLException {
        return new BidHistoryDto(
                result.getLong("history_id"),
                result.getLong("bid_id"),
                result.getLong("auction_id"),
                result.getLong("bidder_id"),
                result.getLong("amount_cents"),
                ResultSetValues.requiredInstant(result, "bid_time"),
                ResultSetValues.requiredEnum(result, "status", BidStatus.class),
                result.getString("client_request_id"),
                result.getLong("auction_version"),
                ResultSetValues.requiredInstant(result, "recorded_at"));
    }
}
