package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.Role;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps the users + wallets join used by the user repository. */
public final class UserRowMapper {
    private UserRowMapper() {
    }

    public static UserDto map(ResultSet result) throws SQLException {
        return new UserDto(
                result.getLong("user_id"),
                result.getString("account"),
                ResultSetValues.requiredEnum(result, "role", Role.class),
                result.getLong("balance_cents"),
                result.getLong("frozen_cents"),
                ResultSetValues.requiredInstant(result, "created_at"),
                ResultSetValues.requiredInstant(result, "updated_at"));
    }
}
