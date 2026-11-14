package com.btl.team10.server.bidding;

import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.network.EventPublisher;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PlaceBidService {
    private final Connection connection;
    private final EventPublisher eventPublisher;
    private final ConcurrentHashMap<Long, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    public PlaceBidService(Connection connection, EventPublisher eventPublisher) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public ResponseEnvelope placeBid(String requestId, String sessionId, long bidderId, long auctionId, long amountCents) throws SQLException {
        if (requestId == null || requestId.isBlank() || auctionId <= 0 || amountCents <= 0) {
            return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.VALIDATION_ERROR, "Invalid bid parameters");
        }

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            String cached = findReceipt(requestId);
            if (cached != null) {
                return JsonCodec.fromJson(cached, ResponseEnvelope.class);
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                AuctionState auction = loadAuction(auctionId);
                if (auction == null) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.NOT_FOUND, "Auction not found");
                }
                if (!"OPEN".equals(auction.status) && !"RUNNING".equals(auction.status)) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.AUCTION_NOT_OPEN, "Auction is not open");
                }
                Instant now = Instant.now();
                if (!Instant.parse(auction.endAt).isAfter(now)) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.AUCTION_NOT_OPEN, "Auction has ended");
                }
                if (auction.sellerId == bidderId) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.FORBIDDEN, "Seller cannot bid on own auction");
                }

                long minRequired = auction.winnerId == null
                        ? auction.startPriceCents
                        : auction.currentPriceCents + auction.minIncrementCents;
                if (amountCents < minRequired) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.BID_TOO_LOW, "Bid amount is below minimum required: " + minRequired);
                }

                WalletState bidderWallet = loadWallet(bidderId);
                if (bidderWallet == null) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.NOT_FOUND, "Wallet not found");
                }
                long currentlyFrozenForThisAuction = (auction.winnerId != null && auction.winnerId == bidderId)
                        ? auction.currentPriceCents
                        : 0L;
                long available = bidderWallet.balanceCents - bidderWallet.frozenCents + currentlyFrozenForThisAuction;
                if (available < amountCents) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
                }

                int updatedAuction = updateAuctionOptimistic(auctionId, amountCents, bidderId, auction.version);
                if (updatedAuction == 0) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.CONFLICT, "Concurrent bid conflict");
                }

                markPreviousBidsOutbid(auctionId);
                String nowIso = now.toString();
                long bidId = insertBid(auctionId, bidderId, amountCents, nowIso, requestId);

                if (auction.winnerId != null && auction.winnerId == bidderId) {
                    long delta = amountCents - auction.currentPriceCents;
                    if (!adjustFrozen(bidderId, delta, bidderWallet.version)) {
                        connection.rollback();
                        return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.CONFLICT, "Wallet update conflict");
                    }
                } else {
                    if (!adjustFrozen(bidderId, amountCents, bidderWallet.version)) {
                        connection.rollback();
                        return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.CONFLICT, "Wallet update conflict");
                    }
                    if (auction.winnerId != null) {
                        WalletState prevWallet = loadWallet(auction.winnerId);
                        if (prevWallet == null || !adjustFrozen(auction.winnerId, -auction.currentPriceCents, prevWallet.version)) {
                            connection.rollback();
                            return ResponseEnvelope.failure(requestId, "PLACE_BID", ErrorCode.CONFLICT, "Previous bidder wallet conflict");
                        }
                    }
                }

                long seq = nextSequence(auctionId);
                JsonObject eventPayload = new JsonObject();
                eventPayload.addProperty("bidId", bidId);
                eventPayload.addProperty("auctionId", auctionId);
                eventPayload.addProperty("bidderId", bidderId);
                eventPayload.addProperty("amountCents", amountCents);
                eventPayload.addProperty("bidTime", nowIso);
                insertEvent(auctionId, seq, EventType.BID_ACCEPTED.name(), eventPayload.toString(), nowIso);

                BidDto bidDto = new BidDto(bidId, auctionId, bidderId, amountCents, now, BidStatus.ACTIVE, requestId);
                ResponseEnvelope response = ResponseEnvelope.success(requestId, "PLACE_BID", sessionId, JsonCodec.toJson(bidDto));
                saveReceipt(requestId, "PLACE_BID", JsonCodec.toJson(response), nowIso);

                connection.commit();

                eventPublisher.publishToAll(EventType.BID_ACCEPTED, auctionId, seq, eventPayload.toString());
                if (auction.winnerId != null && auction.winnerId != bidderId) {
                    JsonObject outbidPayload = new JsonObject();
                    outbidPayload.addProperty("auctionId", auctionId);
                    outbidPayload.addProperty("newPriceCents", amountCents);
                    outbidPayload.addProperty("newLeaderId", bidderId);
                    eventPublisher.publishToUser(auction.winnerId, EventType.OUTBID, auctionId, seq, outbidPayload.toString());
                }

                return response;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<BidDto> listBidsByAuction(long auctionId) throws SQLException {
        String sql = "SELECT bid_id, auction_id, bidder_id, amount_cents, bid_time, status, client_request_id "
                + "FROM bids WHERE auction_id = ? ORDER BY amount_cents DESC, bid_time ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BidDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new BidDto(
                            rs.getLong("bid_id"),
                            rs.getLong("auction_id"),
                            rs.getLong("bidder_id"),
                            rs.getLong("amount_cents"),
                            Instant.parse(rs.getString("bid_time")),
                            BidStatus.valueOf(rs.getString("status")),
                            rs.getString("client_request_id")
                    ));
                }
                return list;
            }
        }
    }

    private String findReceipt(String requestId) throws SQLException {
        String sql = "SELECT response_json FROM request_receipts WHERE request_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("response_json") : null;
            }
        }
    }

    private void saveReceipt(String requestId, String action, String responseJson, String createdAt) throws SQLException {
        String sql = "INSERT INTO request_receipts (request_id, action, response_json, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, action);
            ps.setString(3, responseJson);
            ps.setString(4, createdAt);
            ps.executeUpdate();
        }
    }

    private AuctionState loadAuction(long auctionId) throws SQLException {
        String sql = "SELECT a.start_price_cents, a.current_price_cents, a.min_increment_cents, a.end_at, a.status, a.version, a.winner_id, i.seller_id "
                + "FROM auctions a JOIN items i ON a.item_id = i.item_id WHERE a.auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rawWinner = rs.getLong("winner_id");
                Long winnerId = rs.wasNull() ? null : rawWinner;
                return new AuctionState(
                        rs.getLong("start_price_cents"),
                        rs.getLong("current_price_cents"),
                        rs.getLong("min_increment_cents"),
                        rs.getString("end_at"),
                        rs.getString("status"),
                        rs.getInt("version"),
                        winnerId,
                        rs.getLong("seller_id")
                );
            }
        }
    }

    private WalletState loadWallet(long userId) throws SQLException {
        String sql = "SELECT balance_cents, frozen_cents, version FROM wallets WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new WalletState(rs.getLong("balance_cents"), rs.getLong("frozen_cents"), rs.getInt("version"));
            }
        }
    }

    private int updateAuctionOptimistic(long auctionId, long newPrice, long winnerId, int expectedVersion) throws SQLException {
        String sql = "UPDATE auctions SET current_price_cents = ?, winner_id = ?, status = 'RUNNING', version = version + 1 "
                + "WHERE auction_id = ? AND version = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newPrice);
            ps.setLong(2, winnerId);
            ps.setLong(3, auctionId);
            ps.setInt(4, expectedVersion);
            return ps.executeUpdate();
        }
    }

    private void markPreviousBidsOutbid(long auctionId) throws SQLException {
        String sql = "UPDATE bids SET status = 'OUTBID' WHERE auction_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.executeUpdate();
        }
    }

    private long insertBid(long auctionId, long bidderId, long amountCents, String bidTime, String requestId) throws SQLException {
        String sql = "INSERT INTO bids (auction_id, bidder_id, amount_cents, bid_time, status, client_request_id) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, bidderId);
            ps.setLong(3, amountCents);
            ps.setString(4, bidTime);
            ps.setString(5, requestId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated bid_id");
            }
        }
    }

    private boolean adjustFrozen(long userId, long deltaCents, int expectedVersion) throws SQLException {
        String sql = "UPDATE wallets SET frozen_cents = frozen_cents + ?, version = version + 1 "
                + "WHERE user_id = ? AND version = ? AND frozen_cents + ? >= 0 AND frozen_cents + ? <= balance_cents";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, deltaCents);
            ps.setLong(2, userId);
            ps.setInt(3, expectedVersion);
            ps.setLong(4, deltaCents);
            ps.setLong(5, deltaCents);
            return ps.executeUpdate() > 0;
        }
    }

    private long nextSequence(long auctionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence), 0) + 1 FROM auction_events WHERE auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }

    private void insertEvent(long auctionId, long sequence, String eventType, String payloadJson, String occurredAt) throws SQLException {
        String sql = "INSERT INTO auction_events (auction_id, sequence, event_type, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, sequence);
            ps.setString(3, eventType);
            ps.setString(4, payloadJson);
            ps.setString(5, occurredAt);
            ps.executeUpdate();
        }
    }

    private record AuctionState(
            long startPriceCents,
            long currentPriceCents,
            long minIncrementCents,
            String endAt,
            String status,
            int version,
            Long winnerId,
            long sellerId
    ) {}

    private record WalletState(long balanceCents, long frozenCents, int version) {}
}