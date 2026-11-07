package com.btl.team10.server.repository;

import com.btl.team10.server.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ItemRepository {

    public record ItemRecord(long itemId, long sellerId, String name, String description, String itemType, String imageBase64) {}

    public Optional<ItemRecord> createItem(long sellerId, String name, String description, String itemType, String imageBase64) {
        String sql = "INSERT INTO items (seller_id, name, description, item_type, image_base64) VALUES (?, ?, ?, ?, ?)";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, sellerId);
                stmt.setString(2, name);
                stmt.setString(3, description == null ? "" : description);
                stmt.setString(4, itemType);
                stmt.setString(5, imageBase64);

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            long id = keys.getLong(1);
                            return Optional.of(new ItemRecord(id, sellerId, name, description, itemType, imageBase64));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi tao item: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<ItemRecord> findBySellerId(long sellerId) {
        List<ItemRecord> list = new ArrayList<>();
        String sql = "SELECT item_id, seller_id, name, description, item_type, image_base64 FROM items WHERE seller_id = ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, sellerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ItemRecord(
                                rs.getLong("item_id"),
                                rs.getLong("seller_id"),
                                rs.getString("name"),
                                rs.getString("description"),
                                rs.getString("item_type"),
                                rs.getString("image_base64")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi tim items cua seller: " + e.getMessage());
        }
        return list;
    }
}