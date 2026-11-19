package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.SettlementDto;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class SettlementRowMapper {
    private SettlementRowMapper() {
    }

    public static SettlementDto map(ResultSet result) throws SQLException {
        return new SettlementDto(
                result.getLong("settlement_id"),
                result.getLong("auction_id"),
                ResultSetValues.nullableLong(result, "winner_id"),
                result.getLong("seller_id"),
                result.getLong("winning_amount_cents"),
                result.getString("request_id"),
                ResultSetValues.requiredInstant(result, "settled_at"));
    }
}
