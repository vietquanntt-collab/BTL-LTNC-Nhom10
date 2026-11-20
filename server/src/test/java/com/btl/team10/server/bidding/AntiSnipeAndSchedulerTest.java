package com.btl.team10.server.bidding;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
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

class AntiSnipeAndSchedulerTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private EventPublisher eventPublisher;
    private PlaceBidService placeBidService;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("antisnipe-test.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        Path migrationsRoot = Path.of("database").toAbsolutePath().normalize();
        if (!migrationsRoot.toFile().exists()) {
            migrationsRoot = Path.of("..", "database").toAbsolutePath().normalize();
        }
        DatabaseInitializer.initialize(connection, migrationsRoot, false);

        insertUser(1L, "seller1", "SELLER", 1_000_000L);
        insertUser(2L, "bidder1", "BIDDER", 500_000L);
        insertUser(3L, "bidder2", "BIDDER", 500_000L);
        insertItem(10L, 1L, "Vintage Watch");
        insertAuction(100L, 10L, 100_000L, 10_000L, Instant.now().plusSeconds(300).toString());

        eventPublisher = new EventPublisher();
        placeBidService = new PlaceBidService(
                connection,
                eventPublisher,
                new JdbcAutoBidRuleRepository(connection),
                new JdbcWalletRepository(connection),
                new AntiSnipePolicy(45L, 25L, 2));
        settlementService = new SettlementService(
                connection,
                new JdbcSettlementRepository(connection),
                new JdbcWalletRepository(connection),
                eventPublisher);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void bidOutsideAntiSnipeWindow_doesNotExtendAuction() throws Exception {
        List<EventEnvelope> events = Collections.synchronizedList(new ArrayList<>());
        eventPublisher.subscribe((env, target) -> {
            if (target == null) events.add(env);
        });

        Instant initialEndAt = getEndAt(100L);
        assertTrue(placeBidService.placeBid("bid-early", "s1", 2L, 100L, 100_000L).ok());

        assertEquals(initialEndAt, getEndAt(100L));
        assertEquals(0, getExtensionCount(100L));
        assertFalse(events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_EXTENDED));
    }

    @Test
    void bidInsideAntiSnipeWindow_extendsUntilMaxExtensionsReached() throws Exception {
        Instant nearEnd = Instant.now().plusSeconds(12);
        updateEndAt(100L, nearEnd);

        assertTrue(placeBidService.placeBid("snipe-1", "s1", 2L, 100L, 100_000L).ok());
        assertEquals(nearEnd.plusSeconds(25), getEndAt(100L));
        assertEquals(1, getExtensionCount(100L));

        assertTrue(placeBidService.placeBid("snipe-2", "s2", 3L, 100L, 110_000L).ok());
        assertEquals(nearEnd.plusSeconds(50), getEndAt(100L));
        assertEquals(2, getExtensionCount(100L));

        assertTrue(placeBidService.placeBid("snipe-3", "s1", 2L, 100L, 120_000L).ok());
        assertEquals(nearEnd.plusSeconds(50), getEndAt(100L));
        assertEquals(2, getExtensionCount(100L));
    }

    @Test
    void scheduler_closesExpiredAuction_andSettlesWinner() throws Exception {
        assertTrue(placeBidService.placeBid("bid-sched", "s1", 2L, 100L, 150_000L).ok());
        updateEndAt(100L, Instant.now().minusSeconds(5));

        List<EventEnvelope> events = Collections.synchronizedList(new ArrayList<>());
        eventPublisher.subscribe((env, target) -> {
            if (target == null) events.add(env);
        });

        AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(
                connection, eventPublisher, new Object(), settlementService);
        scheduler.closeExpiredAuctions();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, winner_id FROM auctions WHERE auction_id = 100");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals("PAID", rs.getString("status"));
            assertEquals(2L, rs.getLong("winner_id"));
        }
        assertEquals(350_000L, getBalanceCents(2L));
        assertEquals(0L, getFrozenCents(2L));
        assertEquals(1_150_000L, getBalanceCents(1L));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_FINISHED));
        assertTrue(events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_SETTLED));
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

    private void updateEndAt(long auctionId, Instant endAt) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE auctions SET end_at = ? WHERE auction_id = ?")) {
            ps.setString(1, endAt.toString());
            ps.setLong(2, auctionId);
            ps.executeUpdate();
        }
    }

    private Instant getEndAt(long auctionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT end_at FROM auctions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return Instant.parse(rs.getString(1));
            }
        }
    }

    private int getExtensionCount(long auctionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT extension_count FROM auctions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private long getBalanceCents(long userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance_cents FROM wallets WHERE user_id = ?")) {
            ps.setLong(1, userId);
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