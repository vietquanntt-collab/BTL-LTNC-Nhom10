package com.btl.team10.server.repository;

import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    Optional<ItemDto> findById(long itemId) throws SQLException;

    List<ItemDto> findBySeller(long sellerId) throws SQLException;

    long insert(CreateItemCommand command) throws SQLException;
}
