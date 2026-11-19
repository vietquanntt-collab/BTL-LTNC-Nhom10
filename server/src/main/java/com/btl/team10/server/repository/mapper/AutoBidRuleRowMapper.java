package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.AutoBidRuleDto;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class AutoBidRuleRowMapper {
    private AutoBidRuleRowMapper() {
    }

    public static AutoBidRuleDto map(ResultSet result) throws SQLException {
        return new AutoBidRuleDto(
                result.getLong("rule_id"),
                result.getLong("auction_id"),
                result.getLong("bidder_id"),
                result.getLong("max_amount_cents"),
                result.getLong("increment_cents"),
                ResultSetValues.requiredInstant(result, "registered_at"),
                result.getInt("active") == 1);
    }
}
