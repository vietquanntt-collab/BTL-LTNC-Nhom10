package com.btl.team10.server.bidding;

import com.btl.team10.common.protocol.EventType;
import com.btl.team10.server.network.EventPublisher;
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
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auction-expiration-scheduler");
        t.setDaemon(true);
        return t;
    });

    public AuctionExpirationScheduler(Connection connection, EventPublisher eventPublisher, Object databaseLock) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.databaseLock = Objects.requireNonNull(databaseLock, "databaseLock");
    }

    public void start() {
        executor.scheduleAtFixedRate(this::closeExpiredAuctionsSafe, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void closeExpiredAuctionsSafe() {
        synchronized (databaseLock) {
            try {
                closeExpiredAuctions();
            } catch (Exception e) {
                System.err.println("Scheduler error: " + e.getMessage());
            }
        }
    }

    public void closeExpiredAuctions() throws SQLException {
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

        if (expiredList.isEmpty()) {
            return;
        }

        boolean prevAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (ExpiredAuction item : expiredList) {
                try (PreparedStatement updateAuction = connection.prepareStatement(
                        "UPDATE auctions SET status = 'FINISHED', closed_at = ?, version = version + 1 "
                                + "WHERE auction_id = ? AND status IN ('OPEN', 'RUNNING')")) {
                    updateAuction.setString(1, nowIso);
                    updateAuction.setLong(2, item.auctionId);
                    if (updateAuction.executeUpdate() == 0) {
                        continue;
                    }
                }

                if (item.winnerId != null) {
                    try (PreparedStatement updateBid = connection.prepareStatement(
                            "UPDATE bids SET status = 'WINNER' WHERE auction_id = ? AND status = 'ACTIVE'")) {
                        updateBid.setLong(1, item.auctionId);
                        updateBid.executeUpdate();
                    }
                }

                long seq = nextSequence(item.auctionId);
                JsonObject payload = new JsonObject();
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
                eventPublisher.publishToAll(EventType.AUCTION_FINISHED, item.auctionId, seq, payload.toString());
            }
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(prevAutoCommit);
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