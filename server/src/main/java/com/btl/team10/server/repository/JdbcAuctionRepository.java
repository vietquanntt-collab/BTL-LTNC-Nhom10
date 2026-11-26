package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.server.repository.mapper.AuctionRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcAuctionRepository implements AuctionRepository {
    private static final String AUCTION_COLUMNS =
            "auction_id, item_id, start_price_cents, current_price_cents, min_increment_cents,"
                    + " start_at, end_at, status, version, extension_count, winner_id, closed_at, created_at";
    private final Connection connection;

    public JdbcAuctionRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized Optional<AuctionDto> findById(long auctionId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + AUCTION_COLUMNS + " FROM auctions WHERE auction_id = ?")) {
            statement.setLong(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public synchronized List<AuctionDto> findByStatus(AuctionStatus status) throws SQLException {
        if (status == null) {
            throw new ValidationException("status", "must not be null");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + AUCTION_COLUMNS + " FROM auctions WHERE status = ? ORDER BY end_at, auction_id")) {
            statement.setString(1, status.name());
            try (ResultSet result = statement.executeQuery()) {
                List<AuctionDto> auctions = new ArrayList<>();
                while (result.next()) {
                    auctions.add(map(result));
                }
                return List.copyOf(auctions);
            }
        }
    }

    @Override
    public synchronized long insert(CreateAuctionCommand command) throws SQLException {
        if (command == null) {
            throw new ValidationException("command", "must not be null");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO auctions(item_id, start_price_cents, current_price_cents, min_increment_cents,"
                        + " start_at, end_at, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.itemId());
            statement.setLong(2, command.startPriceCents());
            statement.setLong(3, command.startPriceCents());
            statement.setLong(4, command.minIncrementCents());
            statement.setString(5, command.startAt().toString());
            statement.setString(6, command.endAt().toString());
            statement.setString(7, AuctionStatus.OPEN.name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return generated auction id");
                }
                return keys.getLong(1);
            }
        }
    }

    private static AuctionDto map(ResultSet result) throws SQLException {
        return AuctionRowMapper.map(result);
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
