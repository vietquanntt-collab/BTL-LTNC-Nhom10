package com.btl.team10.server.repository;

import com.btl.team10.common.dto.SettleAuctionCommand;
import com.btl.team10.common.dto.SettlementDto;
import com.btl.team10.common.dto.SettlementWriteResult;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.server.repository.mapper.SettlementRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

public final class JdbcSettlementRepository implements SettlementRepository {
    private static final String COLUMNS =
            "settlement_id, auction_id, winner_id, seller_id, winning_amount_cents, request_id, settled_at";
    private final Connection connection;

    public JdbcSettlementRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized Optional<SettlementDto> findByAuctionId(long auctionId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM settlements WHERE auction_id = ?")) {
            statement.setLong(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SettlementRowMapper.map(result)) : Optional.empty();
            }
        }
    }

    @Override
    public synchronized SettlementWriteResult settle(SettleAuctionCommand command) throws SQLException {
        if (command == null) {
            throw new ValidationException("command", "must not be null");
        }
        Optional<SettlementDto> existing = findByAuctionId(command.auctionId());
        if (existing.isPresent()) {
            return new SettlementWriteResult(existing.get(), false);
        }

        boolean managesTransaction = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (managesTransaction) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint("settle_auction");
        }
        try {
            validateFinishedAuction(command);
            if (command.winnerId() != null) {
                markWinningBid(command);
            }
            long settlementId = insert(command);
            markAuctionPaid(command);
            SettlementDto settlement = findById(settlementId).orElseThrow(
                    () -> new SQLException("Inserted settlement could not be read back"));
            if (managesTransaction) {
                connection.commit();
            }
            return new SettlementWriteResult(settlement, true);
        } catch (SQLException | RuntimeException error) {
            if (managesTransaction) {
                connection.rollback();
            } else if (savepoint != null) {
                try {
                    connection.rollback(savepoint);
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            // A concurrent scheduler may have inserted the unique auction row.
            Optional<SettlementDto> concurrent = findByAuctionId(command.auctionId());
            if (concurrent.isPresent()) {
                return new SettlementWriteResult(concurrent.get(), false);
            }
            throw error;
        } finally {
            if (managesTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    private long insert(SettleAuctionCommand command) throws SQLException {
        String sql = "INSERT INTO settlements(auction_id, winner_id, seller_id, winning_amount_cents, request_id)"
                + " VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.auctionId());
            if (command.winnerId() == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setLong(2, command.winnerId());
            }
            statement.setLong(3, command.sellerId());
            statement.setLong(4, command.winningAmountCents());
            statement.setString(5, command.requestId().trim());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return generated settlement id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void validateFinishedAuction(SettleAuctionCommand command) throws SQLException {
        String sql = "SELECT a.status, a.winner_id, a.current_price_cents, i.seller_id"
                + " FROM auctions a JOIN items i ON i.item_id = a.item_id"
                + " WHERE a.auction_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, command.auctionId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Auction does not exist: " + command.auctionId());
                }
                if (!"FINISHED".equals(result.getString("status"))) {
                    throw new SQLException("Only FINISHED auctions can be settled");
                }
                long rawWinner = result.getLong("winner_id");
                Long auctionWinner = result.wasNull() ? null : rawWinner;
                if (!Objects.equals(auctionWinner, command.winnerId())) {
                    throw new SQLException("Settlement winner does not match the auction winner");
                }
                if (result.getLong("current_price_cents") != command.winningAmountCents()) {
                    throw new SQLException("Settlement amount does not match the auction price");
                }
                if (result.getLong("seller_id") != command.sellerId()) {
                    throw new SQLException("Settlement seller does not own the auction item");
                }
            }
        }
    }

    private void markAuctionPaid(SettleAuctionCommand command) throws SQLException {
        String sql = "UPDATE auctions SET status = 'PAID', closed_at = COALESCE(closed_at, "
                + "strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))"
                + " WHERE auction_id = ? AND status = 'FINISHED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, command.auctionId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Auction changed before settlement commit");
            }
        }
    }

    private void markWinningBid(SettleAuctionCommand command) throws SQLException {
        String sql = "UPDATE bids SET status = 'WINNER' WHERE auction_id = ? AND bidder_id = ?"
                + " AND amount_cents = ? AND status = 'ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, command.auctionId());
            statement.setLong(2, command.winnerId());
            statement.setLong(3, command.winningAmountCents());
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected exactly one active winning bid, found " + updated);
            }
        }
    }

    private Optional<SettlementDto> findById(long settlementId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM settlements WHERE settlement_id = ?")) {
            statement.setLong(1, settlementId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SettlementRowMapper.map(result)) : Optional.empty();
            }
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }
}
