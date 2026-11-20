package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.WalletDto;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class WalletRowMapper {
    private WalletRowMapper() {
    }

    public static WalletDto map(ResultSet result) throws SQLException {
        return new WalletDto(
                result.getLong("user_id"),
                result.getLong("balance_cents"),
                result.getLong("frozen_cents"),
                result.getLong("version"));
    }
}
