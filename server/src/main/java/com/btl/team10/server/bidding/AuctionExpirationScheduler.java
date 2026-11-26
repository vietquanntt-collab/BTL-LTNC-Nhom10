package com.btl.team10.server.bidding;

import com.btl.team10.common.protocol.EventType;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AuctionExpirationScheduler {
    private final Connection connection;
    private final EventPublisher eventPublisher;
    private final Object databaseLock;
    private final SettlementService settlementService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auction-expiration-scheduler");
        t.setDaemon(true);
        return t;
    });

    public AuctionExpirationScheduler(Connection connection, EventPublisher eventPublisher, Object databaseLock) {
        this(connection,
                eventPublisher,
                databaseLock,
                new SettlementService(
                        connection,
                        new JdbcSettlementRepository(connection),
                        new JdbcWalletRepository(connection),
                        eventPublisher));
    }

    public AuctionExpirationScheduler(Connection connection,
                                      EventPublisher eventPublisher,
                                      Object databaseLock,
                                      SettlementService settlementService) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.databaseLock = Objects.requireNonNull(databaseLock, "databaseLock");
        this.settlementService = Objects.requireNonNull(settlementService, "settlementService");
    }

    public void start() {
        executor.scheduleAtFixedRate(this::closeExpiredAuctionsSafe, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void closeExpiredAuctionsSafe() {
        try {
            closeExpiredAuctions();
        } catch (Exception e) {
            System.err.println("Scheduler error: " + e.getMessage());
        }
    }

    public void closeExpiredAuctions() throws SQLException {
        eventPublisher.beginDeferredBatch();
        try {
            synchronized (databaseLock) {
                closeAndSettleAuctions();
            }
        } catch (SQLException | RuntimeException error) {
            eventPublisher.flushDeferredBatch();
            throw error;
        }
        eventPublisher.flushDeferredBatch();
    }

    private void closeAndSettleAuctions() throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new IllegalStateException("Scheduler requires an independent transaction");
        }
        Instant now = Instant.now();
        String nowIso = now.toString();
        List<ExpiredAuction> expiredList = new ArrayList<>();

        String selectSql = "SELECT auction_id, winner_id, current_price_cents, end_at FROM auctions "
                + "WHERE status IN ('OPEN', 'RUNNING')";
        try (PreparedStatement ps = connection.prepareStatement(selectSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Instant endAt = Instant.parse(rs.getString("end_at"));
                if (!endAt.isAfter(now)) {
                    long rawWinner = rs.getLong("winner_id");
                    Long winnerId = rs.wasNull() ? null : rawWinner;
                    expiredList.add(new ExpiredAuction(
                            rs.getLong("auction_id"),
                            winnerId,
                            rs.getLong("current_price_cents")
                    ));
                }
            }
        }

        for (ExpiredAuction item : expiredList) {
            connection.setAutoCommit(false);
            long seq;
            JsonObject payload = new JsonObject();
            try {
                try (PreparedStatement updateAuction = connection.prepareStatement(
                        "UPDATE auctions SET status = 'FINISHED', closed_at = ?, version = version + 1 "
                                + "WHERE auction_id = ? AND status IN ('OPEN', 'RUNNING')")) {
                    updateAuction.setString(1, nowIso);
                    updateAuction.setLong(2, item.auctionId);
                    if (updateAuction.executeUpdate() == 0) {
                        connection.rollback();
                        continue;
                    }
                }

                seq = nextSequence(item.auctionId);
                payload.addProperty("auctionId", item.auctionId);
                payload.addProperty("finalPriceCents", item.finalPriceCents);
                if (item.winnerId != null) {
                    payload.addProperty("winnerId", item.winnerId);
                }
                payload.addProperty("closedAt", nowIso);

                try (PreparedStatement insertEvent = connection.prepareStatement(
                        "INSERT INTO auction_events (auction_id, sequence, event_type, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?)")) {
                    insertEvent.setLong(1, item.auctionId);
                    insertEvent.setLong(2, seq);
                    insertEvent.setString(3, EventType.AUCTION_FINISHED.name());
                    insertEvent.setString(4, payload.toString());
                    insertEvent.setString(5, nowIso);
                    insertEvent.executeUpdate();
                }

                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
            eventPublisher.publishToAll(EventType.AUCTION_FINISHED, item.auctionId, seq, payload.toString());
        }

        // Retry FINISHED rows left by a crash or a failed transfer on a previous tick.
        // Each transfer owns its transaction; no payment event can escape an uncommitted write.
        List<Long> pending = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT auction_id FROM auctions WHERE status = 'FINISHED' ORDER BY auction_id");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                pending.add(rows.getLong(1));
            }
        }
        SQLException failure = null;
        for (long auctionId : pending) {
            try {
                settlementService.settleFinishedAuction(auctionId, "scheduler-settle-" + auctionId);
            } catch (SQLException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
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

    private record ExpiredAuction(long auctionId, Long winnerId, long finalPriceCents) {}
}
