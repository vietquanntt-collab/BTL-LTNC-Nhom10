package com.btl.team10.server.bidding;

import com.btl.team10.common.dto.SettlementWriteResult;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class SettlementAndEventOrderTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private EventPublisher eventPublisher;
    private PlaceBidService placeBidService;
    private SettlementService settlementService;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("settlement-order-test.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        Path migrationsRoot = Path.of("database").toAbsolutePath().normalize();
        if (!migrationsRoot.toFile().exists()) {
            migrationsRoot = Path.of("..", "database").toAbsolutePath().normalize();
        }
        DatabaseInitializer.initialize(connection, migrationsRoot, false);

        insertUser(1L, "seller1", "SELLER", 1_000_000L);
        insertUser(2L, "bidder1", "BIDDER", 500_000L);
        insertUser(3L, "bidder2", "BIDDER", 500_000L);
        insertItem(10L, 1L, "Signed Jersey");
        insertAuction(100L, 10L, 100_000L, 10_000L, Instant.now().plusSeconds(300).toString());

        eventPublisher = new EventPublisher();
        placeBidService = new PlaceBidService(
                connection,
                eventPublisher,
                new JdbcAutoBidRuleRepository(connection),
                new JdbcWalletRepository(connection),
                new AntiSnipePolicy(30L, 20L, 2));
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
    void settlement_concurrentAndRepeatedCalls_settlesExactlyOnce() throws Exception {
        assertTrue(placeBidService.placeBid("bid-win", "s1", 2L, 100L, 150_000L).ok());
        updateEndAt(100L, Instant.now().minusSeconds(5));

        Object dbLock = new Object();
        AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(
                connection, eventPublisher, dbLock, settlementService);
        scheduler.closeExpiredAuctions();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                final String reqId = "concurrent-settle-" + i;
                tasks.add(() -> {
                    synchronized (dbLock) {
                        SettlementWriteResult res = settlementService.settleFinishedAuction(100L, reqId);
                        return res.created();
                    }
                });
            }
            for (Future<Boolean> future : pool.invokeAll(tasks)) {
                assertFalse(future.get());
            }
        } finally {
            pool.shutdownNow();
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM settlements WHERE auction_id = 100");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        assertEquals(350_000L, getBalanceCents(2L));
        assertEquals(0L, getFrozenCents(2L));
        assertEquals(1_150_000L, getBalanceCents(1L));
    }

    @Test
    void eventOrder_bidAutoBidExtendAndSettle_emitsStrictlyIncreasingSequences() throws Exception {
        List<EventEnvelope> recorded = Collections.synchronizedList(new ArrayList<>());
        eventPublisher.subscribe((envelope, targetUserId) -> recorded.add(envelope));

        assertTrue(placeBidService.configureAutoBid(
                "seq-auto-1", "s1", 2L, 100L, 200_000L, 10_000L, true).ok());

        updateEndAt(100L, Instant.now().plusSeconds(10));
        assertTrue(placeBidService.placeBid("seq-bid-2", "s2", 3L, 100L, 120_000L).ok());

        updateEndAt(100L, Instant.now().minusSeconds(2));
        AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(
                connection, eventPublisher, new Object(), settlementService);
        scheduler.closeExpiredAuctions();

        assertFalse(recorded.isEmpty());
        long previousSeq = 0L;
        for (EventEnvelope event : recorded) {
            assertTrue(event.sequence() > previousSeq,
                    "Expected strictly increasing sequence, got " + event.sequence() + " after " + previousSeq);
            previousSeq = event.sequence();
        }

        List<EventType> types = recorded.stream().map(EventEnvelope::eventType).toList();
        int firstAccepted = types.indexOf(EventType.BID_ACCEPTED);
        int firstExtended = types.indexOf(EventType.AUCTION_EXTENDED);
        int finished = types.indexOf(EventType.AUCTION_FINISHED);
        int settled = types.indexOf(EventType.AUCTION_SETTLED);

        assertTrue(firstAccepted >= 0 && firstExtended > firstAccepted);
        assertTrue(finished > firstExtended && settled > finished);
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
