package com.btl.team10.server.bidding;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutoBidTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private EventPublisher eventPublisher;
    private PlaceBidService placeBidService;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("autobid-test.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        Path migrationsRoot = Path.of("database").toAbsolutePath().normalize();
        if (!migrationsRoot.toFile().exists()) {
            migrationsRoot = Path.of("..", "database").toAbsolutePath().normalize();
        }
        DatabaseInitializer.initialize(connection, migrationsRoot, false);

        insertUser(1L, "seller1", "SELLER", 1_000_000L);
        insertUser(2L, "bidder1", "BIDDER", 500_000L);
        insertUser(3L, "bidder2", "BIDDER", 500_000L);
        insertItem(10L, 1L, "Rare Card");
        insertAuction(100L, 10L, 100_000L, 10_000L, Instant.now().plusSeconds(3600).toString());

        eventPublisher = new EventPublisher();
        placeBidService = new PlaceBidService(connection, eventPublisher);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void autoBid_neverExceedsConfiguredMaxPrice_whenCompetitorBidsHigher() throws Exception {
        List<EventEnvelope> bidder1Events = Collections.synchronizedList(new ArrayList<>());
        eventPublisher.subscribe((envelope, targetUserId) -> {
            if (targetUserId != null && targetUserId == 2L) {
                bidder1Events.add(envelope);
            }
        });

        ResponseEnvelope setRule = placeBidService.configureAutoBid(
                "auto-1", "s1", 2L, 100L, 150_000L, 10_000L, true);
        assertTrue(setRule.ok());
        assertEquals(110_000L, getCurrentPrice(100L));
        assertEquals(2L, getWinnerId(100L));

        ResponseEnvelope higherBid = placeBidService.placeBid("bid-2", "s2", 3L, 100L, 160_000L);
        assertTrue(higherBid.ok());

        assertEquals(160_000L, getCurrentPrice(100L));
        assertEquals(3L, getWinnerId(100L));
        assertEquals(0L, getFrozenCents(2L));
        assertEquals(160_000L, getFrozenCents(3L));
        assertTrue(bidder1Events.stream().anyMatch(e -> e.eventType() == EventType.OUTBID));
    }

    @Test
    void autoBid_twoAutoBiddersCompete_escalatesUpToLowerCapPlusIncrement() throws Exception {
        assertTrue(placeBidService.configureAutoBid(
                "auto-b1", "s1", 2L, 100L, 180_000L, 10_000L, true).ok());
        assertTrue(placeBidService.configureAutoBid(
                "auto-b2", "s2", 3L, 100L, 140_000L, 10_000L, true).ok());

        assertEquals(150_000L, getCurrentPrice(100L));
        assertEquals(2L, getWinnerId(100L));
        assertEquals(150_000L, getFrozenCents(2L));
        assertEquals(0L, getFrozenCents(3L));
    }

    @Test
    void autoBid_deactivatedRule_doesNotCounterBid() throws Exception {
        assertTrue(placeBidService.configureAutoBid(
                "auto-on", "s1", 2L, 100L, 200_000L, 10_000L, true).ok());
        assertTrue(placeBidService.configureAutoBid(
                "auto-off", "s1", 2L, 100L, 200_000L, 10_000L, false).ok());

        assertTrue(placeBidService.placeBid("bid-manual", "s2", 3L, 100L, 120_000L).ok());
        assertEquals(120_000L, getCurrentPrice(100L));
        assertEquals(3L, getWinnerId(100L));
    }

    private void insertUser(long id, String account, String role, long balance) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (user_id, account, password_hash, role) VALUES (?, ?, 'hash', ?)")) {
            ps.setLong(1, id);
            ps.setString(2, account);
            ps.setString(3, role);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO wallets (user_id, balance_cents, frozen_cents) VALUES (?, ?, 0)")) {
            ps.setLong(1, id);
            ps.setLong(2, balance);
            ps.executeUpdate();
        }
    }

    private void insertItem(long itemId, long sellerId, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO items (item_id, seller_id, name, item_type) VALUES (?, ?, ?, 'CARD')")) {
            ps.setLong(1, itemId);
            ps.setLong(2, sellerId);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private void insertAuction(long auctionId, long itemId, long startPrice, long minInc, String endAt) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO auctions (auction_id, item_id, start_price_cents, current_price_cents, min_increment_cents, start_at, end_at, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN')")) {
            ps.setLong(1, auctionId);
            ps.setLong(2, itemId);
            ps.setLong(3, startPrice);
            ps.setLong(4, startPrice);
            ps.setLong(5, minInc);
            ps.setString(6, Instant.now().minusSeconds(60).toString());
            ps.setString(7, endAt);
            ps.executeUpdate();
        }
    }

    private long getCurrentPrice(long auctionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT current_price_cents FROM auctions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private long getWinnerId(long auctionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT winner_id FROM auctions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private long getFrozenCents(long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT frozen_cents FROM wallets WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }
}
