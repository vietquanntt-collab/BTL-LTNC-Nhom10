package com.btl.team10.server.repository;

import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    Optional<ItemDto> findById(long itemId) throws SQLException;

    List<ItemDto> findBySeller(long sellerId) throws SQLException;

    long insert(CreateItemCommand command) throws SQLException;

    // Default method tương thích với ClientHandler cũ
    default long createItem(long sellerId, String title, String category, String description, String imageBase64) throws SQLException {
        ItemType type = ItemType.valueOf(category.toUpperCase());
        return insert(new CreateItemCommand(sellerId, title, description, type, imageBase64));
    }
}