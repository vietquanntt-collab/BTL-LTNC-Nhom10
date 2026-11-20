package com.btl.team10.server.bidding;

import com.btl.team10.common.dto.SettleAuctionCommand;
import com.btl.team10.common.dto.SettlementDto;
import com.btl.team10.common.dto.SettlementWriteResult;
import com.btl.team10.common.dto.WalletDto;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.repository.SettlementRepository;
import com.btl.team10.server.repository.WalletRepository;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

public final class SettlementService {
    private final Connection connection;
    private final SettlementRepository settlementRepository;
    private final WalletRepository walletRepository;
    private final EventPublisher eventPublisher;

    public SettlementService(Connection connection,
                             SettlementRepository settlementRepository,
                             WalletRepository walletRepository,
                             EventPublisher eventPublisher) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.settlementRepository = Objects.requireNonNull(settlementRepository, "settlementRepository");
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public ResponseEnvelope settleAuctionRequest(String requestId, String sessionId,
                                                 long requesterId, String requesterRole,
                                                 long auctionId) throws SQLException {
        if (requestId == null || requestId.isBlank() || auctionId <= 0) {
            return ResponseEnvelope.failure(requestId, "SETTLE_AUCTION",
                    ErrorCode.VALIDATION_ERROR, "Invalid settlement request");
        }
        AuctionSettlementTarget target = loadAuctionTarget(auctionId);
        if (target == null) {
            return ResponseEnvelope.failure(requestId, "SETTLE_AUCTION",
                    ErrorCode.NOT_FOUND, "Auction not found");
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requesterRole);
        if (!isAdmin && target.sellerId != requesterId) {
            return ResponseEnvelope.failure(requestId, "SETTLE_AUCTION",
                    ErrorCode.FORBIDDEN, "Only the auction seller or admin can settle");
        }
        if (!"FINISHED".equals(target.status) && !"PAID".equals(target.status)) {
            return ResponseEnvelope.failure(requestId, "SETTLE_AUCTION",
                    ErrorCode.AUCTION_NOT_OPEN, "Auction must be FINISHED before settlement");
        }

        SettlementWriteResult result = settleFinishedAuction(auctionId, requestId);
        JsonObject payload = new JsonObject();
        payload.addProperty("created", result.created());
        payload.addProperty("settlementId", result.settlement().settlementId());
        payload.addProperty("auctionId", result.settlement().auctionId());
        if (result.settlement().winnerId() != null) {
            payload.addProperty("winnerId", result.settlement().winnerId());
        }
        payload.addProperty("sellerId", result.settlement().sellerId());
        payload.addProperty("winningAmountCents", result.settlement().winningAmountCents());
        payload.addProperty("requestId", result.settlement().requestId());
        payload.addProperty("settledAt", result.settlement().settledAt().toString());
        return ResponseEnvelope.success(requestId, "SETTLE_AUCTION", sessionId, payload.toString());
    }

    public SettlementWriteResult settleFinishedAuction(long auctionId, String requestId) throws SQLException {
        boolean prevAutoCommit = connection.getAutoCommit();
        if (prevAutoCommit) {
            connection.setAutoCommit(false);
        }
        try {
            AuctionSettlementTarget target = loadAuctionTarget(auctionId);
            if (target == null) {
                throw new SQLException("Auction does not exist: " + auctionId);
            }
            long winningAmount = target.winnerId == null ? 0L : target.currentPriceCents;
            SettleAuctionCommand command = new SettleAuctionCommand(
                    auctionId,
                    target.sellerId,
                    target.winnerId,
                    winningAmount,
                    requestId
            );

            SettlementWriteResult writeResult = settlementRepository.settle(command);
            if (!writeResult.created()) {
                if (prevAutoCommit) {
                    connection.commit();
                }
                return writeResult;
            }

            String nowIso = Instant.now().toString();
            WalletDto updatedWinnerWallet = null;
            WalletDto updatedSellerWallet = null;

            if (target.winnerId != null && winningAmount > 0) {
                WalletDto winnerWallet = walletRepository.findByUserId(target.winnerId)
                        .orElseThrow(() -> new SQLException("Winner wallet not found: " + target.winnerId));
                if (!captureWinnerFunds(target.winnerId, winningAmount, winnerWallet.version())) {
                    throw new SQLException("Concurrent wallet conflict capturing winner funds");
                }
                updatedWinnerWallet = walletRepository.findByUserId(target.winnerId).orElse(null);

                WalletDto sellerWallet = walletRepository.findByUserId(target.sellerId)
                        .orElseThrow(() -> new SQLException("Seller wallet not found: " + target.sellerId));
                if (!walletRepository.credit(target.sellerId, winningAmount, sellerWallet.version())) {
                    throw new SQLException("Concurrent wallet conflict crediting seller");
                }
                updatedSellerWallet = walletRepository.findByUserId(target.sellerId).orElse(null);
            }

            SettlementDto settlement = writeResult.settlement();
            long settledSeq = nextSequence(auctionId);
            JsonObject settledPayload = new JsonObject();
            settledPayload.addProperty("settlementId", settlement.settlementId());
            settledPayload.addProperty("auctionId", settlement.auctionId());
            if (settlement.winnerId() != null) {
                settledPayload.addProperty("winnerId", settlement.winnerId());
            }
            settledPayload.addProperty("sellerId", settlement.sellerId());
            settledPayload.addProperty("winningAmountCents", settlement.winningAmountCents());
            settledPayload.addProperty("requestId", settlement.requestId());
            settledPayload.addProperty("settledAt", settlement.settledAt().toString());
            insertEvent(auctionId, settledSeq, EventType.AUCTION_SETTLED.name(), settledPayload.toString(), nowIso);

            long winnerBalanceSeq = -1L;
            JsonObject winnerBalancePayload = null;
            if (updatedWinnerWallet != null) {
                winnerBalanceSeq = nextSequence(auctionId);
                winnerBalancePayload = buildBalancePayload(auctionId, updatedWinnerWallet);
                insertEvent(auctionId, winnerBalanceSeq, EventType.BALANCE_CHANGED.name(),
                        winnerBalancePayload.toString(), nowIso);
            }

            long sellerBalanceSeq = -1L;
            JsonObject sellerBalancePayload = null;
            if (updatedSellerWallet != null) {
                sellerBalanceSeq = nextSequence(auctionId);
                sellerBalancePayload = buildBalancePayload(auctionId, updatedSellerWallet);
                insertEvent(auctionId, sellerBalanceSeq, EventType.BALANCE_CHANGED.name(),
                        sellerBalancePayload.toString(), nowIso);
            }

            if (prevAutoCommit) {
                connection.commit();
            }

            eventPublisher.publishToAll(EventType.AUCTION_SETTLED, auctionId, settledSeq, settledPayload.toString());
            if (winnerBalanceSeq > 0 && winnerBalancePayload != null) {
                eventPublisher.publishToUser(updatedWinnerWallet.userId(), EventType.BALANCE_CHANGED,
                        auctionId, winnerBalanceSeq, winnerBalancePayload.toString());
            }
            if (sellerBalanceSeq > 0 && sellerBalancePayload != null) {
                eventPublisher.publishToUser(updatedSellerWallet.userId(), EventType.BALANCE_CHANGED,
                        auctionId, sellerBalanceSeq, sellerBalancePayload.toString());
            }

            return writeResult;
        } catch (SQLException | RuntimeException e) {
            if (prevAutoCommit) {
                connection.rollback();
            }
            throw e;
        } finally {
            if (prevAutoCommit) {
                connection.setAutoCommit(true);
            }
        }
    }

    private boolean captureWinnerFunds(long winnerId, long amountCents, long expectedVersion) throws SQLException {
        String sql = "UPDATE wallets SET balance_cents = balance_cents - ?, frozen_cents = frozen_cents - ?, "
                + "version = version + 1 WHERE user_id = ? AND version = ? "
                + "AND frozen_cents >= ? AND balance_cents >= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, amountCents);
            ps.setLong(2, amountCents);
            ps.setLong(3, winnerId);
            ps.setLong(4, expectedVersion);
            ps.setLong(5, amountCents);
            ps.setLong(6, amountCents);
            return ps.executeUpdate() == 1;
        }
    }

    private JsonObject buildBalancePayload(long auctionId, WalletDto wallet) {
        JsonObject obj = new JsonObject();
        obj.addProperty("auctionId", auctionId);
        obj.addProperty("userId", wallet.userId());
        obj.addProperty("balanceCents", wallet.balanceCents());
        obj.addProperty("frozenCents", wallet.frozenCents());
        obj.addProperty("availableCents", wallet.balanceCents() - wallet.frozenCents());
        obj.addProperty("version", wallet.version());
        return obj;
    }

    private AuctionSettlementTarget loadAuctionTarget(long auctionId) throws SQLException {
        String sql = "SELECT a.status, a.winner_id, a.current_price_cents, i.seller_id "
                + "FROM auctions a JOIN items i ON a.item_id = i.item_id WHERE a.auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rawWinner = rs.getLong("winner_id");
                Long winnerId = rs.wasNull() ? null : rawWinner;
                return new AuctionSettlementTarget(
                        rs.getString("status"),
                        winnerId,
                        rs.getLong("current_price_cents"),
                        rs.getLong("seller_id")
                );
            }
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

    private record AuctionSettlementTarget(String status, Long winnerId, long currentPriceCents, long sellerId) {}
}