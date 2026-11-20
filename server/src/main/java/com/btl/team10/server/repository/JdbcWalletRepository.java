package com.btl.team10.server.repository;

import com.btl.team10.common.dto.WalletDto;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.server.repository.mapper.WalletRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class JdbcWalletRepository implements WalletRepository {
    private final Connection connection;

    public JdbcWalletRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized Optional<WalletDto> findByUserId(long userId) throws SQLException {
        requirePositive(userId, "userId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT user_id, balance_cents, frozen_cents, version FROM wallets WHERE user_id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(WalletRowMapper.map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public synchronized boolean adjustFrozen(long userId, long deltaCents, long expectedVersion)
            throws SQLException {
        requirePositive(userId, "userId");
        requireNonNegative(expectedVersion, "expectedVersion");
        String sql = "UPDATE wallets SET frozen_cents = frozen_cents + ?, version = version + 1"
                + " WHERE user_id = ? AND version = ?"
                + " AND frozen_cents + ? >= 0 AND frozen_cents + ? <= balance_cents";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, deltaCents);
            statement.setLong(2, userId);
            statement.setLong(3, expectedVersion);
            statement.setLong(4, deltaCents);
            statement.setLong(5, deltaCents);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public synchronized boolean credit(long userId, long amountCents, long expectedVersion)
            throws SQLException {
        requirePositive(userId, "userId");
        if (amountCents <= 0) {
            throw new ValidationException("amountCents", "must be positive");
        }
        requireNonNegative(expectedVersion, "expectedVersion");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE wallets SET balance_cents = balance_cents + ?, version = version + 1"
                        + " WHERE user_id = ? AND version = ?")) {
            statement.setLong(1, amountCents);
            statement.setLong(2, userId);
            statement.setLong(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new ValidationException(field, "must not be negative");
        }
    }
}
