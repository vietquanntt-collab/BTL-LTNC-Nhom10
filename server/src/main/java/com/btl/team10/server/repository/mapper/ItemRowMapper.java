package com.btl.team10.server.repository.mapper;

import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps an items row to the shared item DTO. */
public final class ItemRowMapper {
    private ItemRowMapper() {
    }

    public static ItemDto map(ResultSet result) throws SQLException {
        return new ItemDto(
                result.getLong("item_id"),
                result.getLong("seller_id"),
                result.getString("name"),
                result.getString("description"),
                ResultSetValues.requiredEnum(result, "item_type", ItemType.class),
                result.getString("image_base64"),
                ResultSetValues.requiredInstant(result, "created_at"),
                ResultSetValues.requiredInstant(result, "updated_at"));
    }
}
