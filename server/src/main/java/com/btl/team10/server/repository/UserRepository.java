package com.btl.team10.server.repository;

import com.btl.team10.server.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserRepository {

    public record UserRecord(long userId, String account, String passwordHash, String role) {}

    public Optional<UserRecord> findByAccount(String account) {
        String sql = "SELECT user_id, account, password_hash, role FROM users WHERE account = ?";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, account);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new UserRecord(
                                rs.getLong("user_id"),
                                rs.getString("account"),
                                rs.getString("password_hash"),
                                rs.getString("role")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi truy van findByAccount: " + e.getMessage());
        }
        return Optional.empty();
    }

    public boolean createUser(String account, String passwordHash, String role) {
        String sql = "INSERT INTO users (account, password_hash, role) VALUES (?, ?, ?)";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, account);
                stmt.setString(2, passwordHash);
                stmt.setString(3, role);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Loi tao user: " + e.getMessage());
            return false;
        }
    }
}