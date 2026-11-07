package com.btl.team10.server.repository;

import com.btl.team10.server.config.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AuctionRepository {

    public record AuctionRecord(
            long auctionId,
            long itemId,
            long startPriceCents,
            long currentPriceCents,
            long minIncrementCents,
            String startAt,
            String endAt,
            String status,
            Long winnerId
    ) {}

    public Optional<AuctionRecord> createAuction(long itemId, long startPriceCents, long minIncrementCents, int durationHours) {
        String sql = "INSERT INTO auctions (item_id, start_price_cents, current_price_cents, min_increment_cents, start_at, end_at, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'OPEN')";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                Instant now = Instant.now();
                Instant end = now.plus(durationHours, ChronoUnit.HOURS);
                String startAt = now.toString();
                String endAt = end.toString();

                stmt.setLong(1, itemId);
                stmt.setLong(2, startPriceCents);
                stmt.setLong(3, startPriceCents); // current_price >= start_price
                stmt.setLong(4, minIncrementCents);
                stmt.setString(5, startAt);
                stmt.setString(6, endAt);

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            long id = keys.getLong(1);
                            return Optional.of(new AuctionRecord(id, itemId, startPriceCents, startPriceCents, minIncrementCents, startAt, endAt, "OPEN", null));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi tao auction: " + e.getMessage());
        }
        return Optional.empty();
    }

    public List<AuctionRecord> findActiveAuctions() {
        List<AuctionRecord> list = new ArrayList<>();
        String sql = "SELECT auction_id, item_id, start_price_cents, current_price_cents, min_increment_cents, start_at, end_at, status, winner_id " +
                "FROM auctions WHERE status IN ('OPEN', 'RUNNING')";
        try {
            Connection conn = DatabaseConnection.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long winnerIdVal = rs.getLong("winner_id");
                    Long winnerId = rs.wasNull() ? null : winnerIdVal;

                    list.add(new AuctionRecord(
                            rs.getLong("auction_id"),
                            rs.getLong("item_id"),
                            rs.getLong("start_price_cents"),
                            rs.getLong("current_price_cents"),
                            rs.getLong("min_increment_cents"),
                            rs.getString("start_at"),
                            rs.getString("end_at"),
                            rs.getString("status"),
                            winnerId
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi tim active auctions: " + e.getMessage());
        }
        return list;
    }
}