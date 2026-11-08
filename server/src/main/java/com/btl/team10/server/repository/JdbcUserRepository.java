package com.btl.team10.server.repository;

import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.Role;
import com.btl.team10.server.repository.mapper.UserRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

public final class JdbcUserRepository implements UserRepository {
    private static final String USER_COLUMNS =
            "users.user_id, users.account, users.role, wallets.balance_cents, wallets.frozen_cents,"
                    + " users.created_at, users.updated_at";
    private final Connection connection;

    public JdbcUserRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public Optional<UserDto> findById(long userId) throws SQLException {
        requirePositive(userId, "userId");
        String sql = "SELECT " + USER_COLUMNS
                + " FROM users JOIN wallets ON wallets.user_id = users.user_id"
                + " WHERE users.user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<UserDto> findByAccount(String account) throws SQLException {
        requireNonBlank(account, "account");
        String sql = "SELECT " + USER_COLUMNS
                + " FROM users JOIN wallets ON wallets.user_id = users.user_id"
                + " WHERE users.account = ? COLLATE NOCASE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.trim());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public long insert(String account, String passwordHash, Role role) throws SQLException {
        requireNonBlank(account, "account");
        requireNonBlank(passwordHash, "passwordHash");
        if (role == null) {
            throw new ValidationException("role", "must not be null");
        }
        boolean managesTransaction = connection.getAutoCommit();
        if (managesTransaction) {
            connection.setAutoCommit(false);
        }
        try {
            long userId;
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users(account, password_hash, role) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, account.trim());
                statement.setString(2, passwordHash);
                statement.setString(3, role.name());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("SQLite did not return generated user id");
                    }
                    userId = keys.getLong(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wallets(user_id) VALUES (?)")) {
                statement.setLong(1, userId);
                statement.executeUpdate();
            }
            if (managesTransaction) {
                connection.commit();
            }
            return userId;
        } catch (SQLException | RuntimeException error) {
            if (managesTransaction) {
                connection.rollback();
            }
            throw error;
        } finally {
            if (managesTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    private static UserDto map(ResultSet result) throws SQLException {
        return UserRowMapper.map(result);
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "must not be blank");
        }
    }
}
