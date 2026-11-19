package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AutoBidRuleDto;
import com.btl.team10.common.dto.ConfigureAutoBidCommand;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.server.repository.mapper.AutoBidRuleRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcAutoBidRuleRepository implements AutoBidRuleRepository {
    private static final String COLUMNS =
            "rule_id, auction_id, bidder_id, max_amount_cents, increment_cents, registered_at, active";
    private final Connection connection;

    public JdbcAutoBidRuleRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized Optional<AutoBidRuleDto> findByAuctionAndBidder(long auctionId, long bidderId)
            throws SQLException {
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM auto_bid_rules WHERE auction_id = ? AND bidder_id = ?")) {
            statement.setLong(1, auctionId);
            statement.setLong(2, bidderId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(AutoBidRuleRowMapper.map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public synchronized List<AutoBidRuleDto> findActiveByAuction(long auctionId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM auto_bid_rules"
                        + " WHERE auction_id = ? AND active = 1 ORDER BY registered_at, rule_id")) {
            statement.setLong(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                List<AutoBidRuleDto> rules = new ArrayList<>();
                while (result.next()) {
                    rules.add(AutoBidRuleRowMapper.map(result));
                }
                return List.copyOf(rules);
            }
        }
    }

    @Override
    public synchronized AutoBidRuleDto upsert(ConfigureAutoBidCommand command) throws SQLException {
        if (command == null) {
            throw new ValidationException("command", "must not be null");
        }
        String sql = "INSERT INTO auto_bid_rules(auction_id, bidder_id, max_amount_cents, increment_cents, active)"
                + " VALUES (?, ?, ?, ?, 1)"
                + " ON CONFLICT(auction_id, bidder_id) DO UPDATE SET"
                + " max_amount_cents = excluded.max_amount_cents,"
                + " increment_cents = excluded.increment_cents,"
                + " registered_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), active = 1";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.auctionId());
            statement.setLong(2, command.bidderId());
            statement.setLong(3, command.maxAmountCents());
            statement.setLong(4, command.incrementCents());
            statement.executeUpdate();
        }
        return findByAuctionAndBidder(command.auctionId(), command.bidderId()).orElseThrow(
                () -> new SQLException("Upserted auto-bid rule could not be read back"));
    }

    @Override
    public synchronized boolean deactivate(long auctionId, long bidderId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE auto_bid_rules SET active = 0 WHERE auction_id = ? AND bidder_id = ? AND active = 1")) {
            statement.setLong(1, auctionId);
            statement.setLong(2, bidderId);
            return statement.executeUpdate() == 1;
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
