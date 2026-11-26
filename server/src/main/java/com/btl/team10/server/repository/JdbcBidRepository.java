package com.btl.team10.server.repository;

import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.BidHistoryDto;
import com.btl.team10.common.dto.BidWriteResult;
import com.btl.team10.common.dto.PlaceBidCommand;
import com.btl.team10.common.exception.BidRejectedException;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.server.repository.mapper.BidHistoryRowMapper;
import com.btl.team10.server.repository.mapper.BidRowMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQLite implementation of the week 3 bid data boundary.
 *
 * The auction version is the optimistic concurrency token. A bid updates the
 * auction, bid row and append-only history row in one transaction.
 */
public final class JdbcBidRepository implements BidRepository {
    private static final String BID_COLUMNS =
            "bid_id, auction_id, bidder_id, amount_cents, bid_time, status, client_request_id";
    private static final String HISTORY_COLUMNS =
            "history_id, bid_id, auction_id, bidder_id, amount_cents, bid_time, status,"
                    + " client_request_id, auction_version, recorded_at";

    private final Connection connection;

    public JdbcBidRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    @Override
    public synchronized BidWriteResult placeBid(PlaceBidCommand command) throws SQLException {
        if (command == null) {
            throw new ValidationException("command", "must not be null");
        }

        boolean managesTransaction = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (managesTransaction) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint("place_bid");
        }

        try {
            Optional<BidWriteResult> duplicate = findByRequestInternal(
                    command.auctionId(), command.bidderId(), command.clientRequestId());
            if (duplicate.isPresent()) {
                finishTransaction(managesTransaction);
                return duplicate.get();
            }

            AuctionSnapshot auction = readAuction(command.auctionId());
            validateAuction(auction, command);

            long nextVersion = auction.version() + 1;
            Instant bidTime = Instant.now();
            int updated = updateAuctionPrice(command, auction, nextVersion);
            if (updated != 1) {
                throw new BidRejectedException(
                        ErrorCode.CONFLICT, "Auction changed while the bid was being placed");
            }

            markPreviousBidsOutbid(command.auctionId());
            long bidId = insertBid(command, bidTime);
            insertHistory(bidId, command, bidTime, nextVersion);
            BidDto bid = findBidById(bidId).orElseThrow(
                    () -> new SQLException("Inserted bid could not be read back"));
            BidWriteResult result = new BidWriteResult(bid, nextVersion, command.amountCents());
            finishTransaction(managesTransaction);
            return result;
        } catch (SQLException | RuntimeException error) {
            rollback(managesTransaction, savepoint, error);
            throw error;
        } finally {
            if (managesTransaction) {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public synchronized List<BidHistoryDto> findHistory(long auctionId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + HISTORY_COLUMNS + " FROM bid_history"
                        + " WHERE auction_id = ? ORDER BY auction_version, history_id")) {
            statement.setLong(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                List<BidHistoryDto> history = new ArrayList<>();
                while (result.next()) {
                    history.add(BidHistoryRowMapper.map(result));
                }
                return List.copyOf(history);
            }
        }
    }

    @Override
    public synchronized Optional<BidWriteResult> findByRequest(
            long auctionId, long bidderId, String clientRequestId) throws SQLException {
        requirePositive(auctionId, "auctionId");
        requirePositive(bidderId, "bidderId");
        requireRequestId(clientRequestId);
        return findByRequestInternal(auctionId, bidderId, clientRequestId);
    }

    private Optional<BidWriteResult> findByRequestInternal(
            long auctionId, long bidderId, String clientRequestId) throws SQLException {
        String sql = "SELECT b." + BID_COLUMNS.replace(", ", ", b.")
                + ", COALESCE(h.auction_version, a.version) AS auction_version"
                + " FROM bids b JOIN auctions a ON a.auction_id = b.auction_id"
                + " LEFT JOIN bid_history h ON h.bid_id = b.bid_id"
                + " WHERE b.auction_id = ? AND b.bidder_id = ? AND b.client_request_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, auctionId);
            statement.setLong(2, bidderId);
            statement.setString(3, clientRequestId.trim());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                BidDto bid = BidRowMapper.map(result);
                // A retry must reproduce the result of the original write.
                // The auction may have moved on since then, so its current
                // price is not the price that belongs to this request.
                return Optional.of(new BidWriteResult(
                        bid, result.getLong("auction_version"), bid.amountCents()));
            }
        }
    }

    private AuctionSnapshot readAuction(long auctionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT current_price_cents, min_increment_cents, status, version, end_at"
                        + " FROM auctions WHERE auction_id = ?")) {
            statement.setLong(1, auctionId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new BidRejectedException(ErrorCode.NOT_FOUND, "Auction does not exist");
                }
                return new AuctionSnapshot(
                        result.getLong("current_price_cents"),
                        result.getLong("min_increment_cents"),
                        AuctionStatus.valueOf(result.getString("status")),
                        result.getLong("version"),
                        Instant.parse(result.getString("end_at")));
            }
        }
    }

    private static void validateAuction(AuctionSnapshot auction, PlaceBidCommand command) {
        if (auction.status() != AuctionStatus.RUNNING) {
            throw new BidRejectedException(
                    ErrorCode.AUCTION_NOT_OPEN, "Auction is not accepting bids");
        }
        if (!Instant.now().isBefore(auction.endAt())) {
            throw new BidRejectedException(ErrorCode.AUCTION_NOT_OPEN, "Auction has ended");
        }
        if (auction.version() != command.expectedAuctionVersion()) {
            throw new BidRejectedException(
                    ErrorCode.CONFLICT, "Auction version is stale; reload before bidding");
        }
        long minimum = Math.addExact(auction.currentPriceCents(), auction.minIncrementCents());
        if (command.amountCents() < minimum) {
            throw new BidRejectedException(
                    ErrorCode.BID_TOO_LOW, "Bid must be at least " + minimum + " cents");
        }
    }

    private int updateAuctionPrice(PlaceBidCommand command, AuctionSnapshot auction, long nextVersion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE auctions SET current_price_cents = ?, version = ?"
                        + " WHERE auction_id = ? AND version = ? AND status = ?")) {
            statement.setLong(1, command.amountCents());
            statement.setLong(2, nextVersion);
            statement.setLong(3, command.auctionId());
            statement.setLong(4, auction.version());
            statement.setString(5, AuctionStatus.RUNNING.name());
            return statement.executeUpdate();
        }
    }

    private void markPreviousBidsOutbid(long auctionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bids SET status = ? WHERE auction_id = ? AND status = ?")) {
            statement.setString(1, BidStatus.OUTBID.name());
            statement.setLong(2, auctionId);
            statement.setString(3, BidStatus.ACTIVE.name());
            statement.executeUpdate();
        }
    }

    private long insertBid(PlaceBidCommand command, Instant bidTime) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bids(auction_id, bidder_id, amount_cents, bid_time, status, client_request_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, command.auctionId());
            statement.setLong(2, command.bidderId());
            statement.setLong(3, command.amountCents());
            statement.setString(4, bidTime.toString());
            statement.setString(5, BidStatus.ACTIVE.name());
            statement.setString(6, command.clientRequestId());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return generated bid id");
                }
                return keys.getLong(1);
            }
        }
    }

    private void insertHistory(long bidId, PlaceBidCommand command, Instant bidTime, long auctionVersion)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bid_history(bid_id, auction_id, bidder_id, amount_cents, bid_time, status,"
                        + " client_request_id, auction_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, bidId);
            statement.setLong(2, command.auctionId());
            statement.setLong(3, command.bidderId());
            statement.setLong(4, command.amountCents());
            statement.setString(5, bidTime.toString());
            statement.setString(6, BidStatus.ACTIVE.name());
            statement.setString(7, command.clientRequestId());
            statement.setLong(8, auctionVersion);
            statement.executeUpdate();
        }
    }

    private Optional<BidDto> findBidById(long bidId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + BID_COLUMNS + " FROM bids WHERE bid_id = ?")) {
            statement.setLong(1, bidId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(BidRowMapper.map(result)) : Optional.empty();
            }
        }
    }

    private void finishTransaction(boolean managesTransaction) throws SQLException {
        if (managesTransaction) {
            connection.commit();
        }
    }

    private void rollback(boolean managesTransaction, Savepoint savepoint, Throwable error)
            throws SQLException {
        if (managesTransaction) {
            connection.rollback();
        } else if (savepoint != null) {
            try {
                connection.rollback(savepoint);
            } catch (SQLException rollbackError) {
                error.addSuppressed(rollbackError);
            }
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new ValidationException(field, "must be positive");
        }
    }

    private static void requireRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ValidationException("clientRequestId", "must not be blank");
        }
    }

    private record AuctionSnapshot(
            long currentPriceCents,
            long minIncrementCents,
            AuctionStatus status,
            long version,
            Instant endAt) {
    }
}
