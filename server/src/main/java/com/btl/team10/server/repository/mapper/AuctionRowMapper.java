package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.model.AuctionStatus;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps an auctions row to the shared auction DTO. */
public final class AuctionRowMapper {
    private AuctionRowMapper() {
    }

    public static AuctionDto map(ResultSet result) throws SQLException {
        return new AuctionDto(
                result.getLong("auction_id"),
                result.getLong("item_id"),
                result.getLong("start_price_cents"),
                result.getLong("current_price_cents"),
                result.getLong("min_increment_cents"),
                ResultSetValues.requiredInstant(result, "start_at"),
                ResultSetValues.requiredInstant(result, "end_at"),
                ResultSetValues.requiredEnum(result, "status", AuctionStatus.class),
                result.getLong("version"),
                result.getInt("extension_count"),
                ResultSetValues.nullableLong(result, "winner_id"),
                ResultSetValues.nullableInstant(result, "closed_at"),
                ResultSetValues.requiredInstant(result, "created_at"));
    }
}
