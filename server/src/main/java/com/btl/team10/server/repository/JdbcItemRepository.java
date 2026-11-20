package com.btl.team10.server.repository;

import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.server.repository.mapper.ItemRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcItemRepository implements ItemRepository {
    private static final String ITEM_COLUMNS =
            "item_id, seller_id, name, description, item_type, image_base64, created_at, updated_at";
    private final Connection connection;

    public JdbcItemRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized Optional<ItemDto> findById(long itemId) throws SQLException {
        requirePositive(itemId, "itemId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ITEM_COLUMNS + " FROM items WHERE item_id = ?")) {
            statement.setLong(1, itemId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public synchronized List<ItemDto> findBySeller(long sellerId) throws SQLException {
        requirePositive(sellerId, "sellerId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + ITEM_COLUMNS + " FROM items WHERE seller_id = ? ORDER BY item_id")) {
            statement.setLong(1, sellerId);
            try (ResultSet result = statement.executeQuery()) {
                List<ItemDto> items = new ArrayList<>();
                while (result.next()) {
                    items.add(map(result));
                }
                return List.copyOf(items);
            }
        }
    }

    @Override
    public synchronized long insert(CreateItemCommand command) throws SQLException {
        if (command == null) {
            throw new ValidationException("command", "must not be null");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO items(seller_id, name, description, item_type, image_base64) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.sellerId());
            statement.setString(2, command.name().trim());
            statement.setString(3, command.description());
            statement.setString(4, command.itemType().name());
            if (command.imageBase64() == null || command.imageBase64().isBlank()) {
                statement.setNull(5, java.sql.Types.VARCHAR);
            } else {
                statement.setString(5, command.imageBase64());
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return generated item id");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public synchronized boolean update(long itemId, long sellerId, String name,
                                       String description, ItemType itemType) throws SQLException {
        requirePositive(itemId, "itemId");
        requirePositive(sellerId, "sellerId");
        if (name == null || name.isBlank() || name.trim().length() > 200) {
            throw new ValidationException("name", "must contain 1 to 200 non-blank characters");
        }
        if (itemType == null) {
            throw new ValidationException("itemType", "must not be null");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE items SET name = ?, description = ?, item_type = ?,"
                        + " updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')"
                        + " WHERE item_id = ? AND seller_id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM auctions WHERE auctions.item_id = items.item_id)")) {
            statement.setString(1, name.trim());
            statement.setString(2, description == null ? "" : description.trim());
            statement.setString(3, itemType.name());
            statement.setLong(4, itemId);
            statement.setLong(5, sellerId);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public synchronized boolean delete(long itemId, long sellerId) throws SQLException {
        requirePositive(itemId, "itemId");
        requirePositive(sellerId, "sellerId");
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM items WHERE item_id = ? AND seller_id = ?"
                        + " AND NOT EXISTS (SELECT 1 FROM auctions WHERE auctions.item_id = items.item_id)")) {
            statement.setLong(1, itemId);
            statement.setLong(2, sellerId);
            return statement.executeUpdate() == 1;
        }
    }

    private static ItemDto map(ResultSet result) throws SQLException {
        return ItemRowMapper.map(result);
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
