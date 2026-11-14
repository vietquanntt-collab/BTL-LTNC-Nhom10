package com.btl.team10.server.bidding;

import com.btl.team10.common.protocol.ErrorCode;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlaceBidServiceTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private EventPublisher eventPublisher;
    private PlaceBidService placeBidService;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("test-auction.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        Path migrationsRoot = Path.of("database").toAbsolutePath().normalize();
        if (!migrationsRoot.toFile().exists()) {
            migrationsRoot = Path.of("..", "database").toAbsolutePath().normalize();
        }
        DatabaseInitializer.initialize(connection, migrationsRoot, false);

        insertUser(1L, "seller1", "SELLER", 1_000_000L);
        insertUser(2L, "bidder1", "BIDDER", 500_000L);
        insertUser(3L, "bidder2", "BIDDER", 500_000L);
        insertItem(10L, 1L, "Charizard Card");
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
    void placeBid_validAndOutbid_updatesPriceWalletsAndBroadcasts() throws Exception {
        List<String> bidder1Events = new ArrayList<>();
        var ch1 = eventPublisher.register(bidder1Events::add);
        ch1.bindUser(2L);

        ResponseEnvelope r1 = placeBidService.placeBid("req-1", "s1", 2L, 100L, 100_000L);
        assertTrue(r1.ok());
        assertEquals(100_000L, getFrozenCents(2L));

        ResponseEnvelope r2 = placeBidService.placeBid("req-2", "s2", 3L, 100L, 120_000L);
        assertTrue(r2.ok());
        assertEquals(0L, getFrozenCents(2L));
        assertEquals(120_000L, getFrozenCents(3L));
        assertTrue(bidder1Events.stream().anyMatch(e -> e.contains("\"OUTBID\"")));
    }

    @Test
    void placeBid_duplicateRequestId_returnsCachedResponseWithoutDoubleFreeze() throws Exception {
        ResponseEnvelope first = placeBidService.placeBid("req-dup", "s1", 2L, 100L, 110_000L);
        ResponseEnvelope second = placeBidService.placeBid("req-dup", "s1", 2L, 100L, 110_000L);

        assertTrue(first.ok());
        assertEquals(first.payloadJson(), second.payloadJson());
        assertEquals(110_000L, getFrozenCents(2L));
    }

    @Test
    void placeBid_validationErrors_rejectedProperly() throws Exception {
        ResponseEnvelope selfBid = placeBidService.placeBid("req-self", "s0", 1L, 100L, 110_000L);
        assertFalse(selfBid.ok());
        assertEquals(ErrorCode.FORBIDDEN, selfBid.errorCode());

        ResponseEnvelope lowBid = placeBidService.placeBid("req-low", "s1", 2L, 100L, 50_000L);
        assertFalse(lowBid.ok());
        assertEquals(ErrorCode.BID_TOO_LOW, lowBid.errorCode());

        ResponseEnvelope poorBid = placeBidService.placeBid("req-poor", "s1", 2L, 100L, 900_000L);
        assertFalse(poorBid.ok());
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, poorBid.errorCode());
    }

    @Test
    void scheduler_closesExpiredAuctionsAndMarksWinner() throws Exception {
        placeBidService.placeBid("req-win", "s1", 2L, 100L, 150_000L);
        try (PreparedStatement ps = connection.prepareStatement("UPDATE auctions SET end_at = ? WHERE auction_id = 100")) {
            ps.setString(1, Instant.now().minusSeconds(5).toString());
            ps.executeUpdate();
        }

        AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(connection, eventPublisher, new Object());
        scheduler.closeExpiredAuctions();

        try (PreparedStatement ps = connection.prepareStatement("SELECT status, winner_id FROM auctions WHERE auction_id = 100");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("FINISHED", rs.getString("status"));
            assertEquals(2L, rs.getLong("winner_id"));
        }
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