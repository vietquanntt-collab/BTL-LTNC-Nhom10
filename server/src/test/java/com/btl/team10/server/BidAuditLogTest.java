package com.btl.team10.server;

import com.btl.team10.common.dto.BidWriteResult;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.PlaceBidCommand;
import com.btl.team10.common.exception.BidRejectedException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcBidRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Week 3 - Bid Audit Log Integrity Tests")
class BidAuditLogTest {

    @Test
    @DisplayName("1. Log đặt giá thành công và thất bại đều ghi đủ requestId, auctionId, userId")
    void auditLogCapturesRequestIdAuctionIdAndUserIdForAllOutcomes() throws Exception {
        List<String> capturedLogs = new ArrayList<>();
        Logger auditLogger = attachMemoryHandler("audit.bid.single", capturedLogs);

        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection, 2);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);

            PlaceBidCommand validCmd = new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderIds().get(0), 1_050L, "audit-req-ok-1", 0L);
            executeWithAuditLog(bidRepo, auditLogger, validCmd);

            PlaceBidCommand lowCmd = new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderIds().get(1), 1_060L, "audit-req-low-2", 1L);
            assertThrows(BidRejectedException.class,
                    () -> executeWithAuditLog(bidRepo, auditLogger, lowCmd));

            assertEquals(2, capturedLogs.size());

            String acceptedLog = capturedLogs.get(0);
            assertTrue(acceptedLog.contains("requestId=audit-req-ok-1"));
            assertTrue(acceptedLog.contains("auctionId=" + fixture.auctionId()));
            assertTrue(acceptedLog.contains("userId=" + fixture.bidderIds().get(0)));
            assertTrue(acceptedLog.contains("status=ACCEPTED"));

            String rejectedLog = capturedLogs.get(1);
            assertTrue(rejectedLog.contains("requestId=audit-req-low-2"));
            assertTrue(rejectedLog.contains("auctionId=" + fixture.auctionId()));
            assertTrue(rejectedLog.contains("userId=" + fixture.bidderIds().get(1)));
            assertTrue(rejectedLog.contains("status=REJECTED"));
        }
    }

    @Test
    @DisplayName("2. Đối chiếu log đa luồng với dữ liệu lưu trong Database không bị thất lạc bản ghi")
    void concurrentAuditLogsMatchPersistedBidRecords() throws Exception {
        List<String> capturedLogs = new CopyOnWriteArrayList<>();
        Logger auditLogger = attachMemoryHandler("audit.bid.concurrent", capturedLogs);

        try (Connection connection = openDatabase()) {
            int clientCount = 8;
            Fixture fixture = createRunningAuction(connection, clientCount);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);
            Object dbLock = new Object();

            ExecutorService pool = Executors.newFixedThreadPool(clientCount);
            CountDownLatch ready = new CountDownLatch(clientCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(clientCount);

            for (int i = 0; i < clientCount; i++) {
                final long userId = fixture.bidderIds().get(i);
                final String reqId = "audit-conc-req-" + i;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        synchronized (dbLock) {
                            PlaceBidCommand cmd = new PlaceBidCommand(
                                    fixture.auctionId(), userId, 1_050L, reqId, 0L);
                            executeWithAuditLog(bidRepo, auditLogger, cmd);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            pool.shutdown();

            assertEquals(clientCount, capturedLogs.size());
            long acceptedLogCount = capturedLogs.stream()
                    .filter(line -> line.contains("status=ACCEPTED"))
                    .count();
            long rejectedLogCount = capturedLogs.stream()
                    .filter(line -> line.contains("status=REJECTED"))
                    .count();

            assertEquals(1L, acceptedLogCount);
            assertEquals(clientCount - 1L, rejectedLogCount);

            for (int i = 0; i < clientCount; i++) {
                String expectedReq = "requestId=audit-conc-req-" + i;
                String expectedUser = "userId=" + fixture.bidderIds().get(i);
                String expectedAuction = "auctionId=" + fixture.auctionId();
                boolean found = capturedLogs.stream().anyMatch(line ->
                        line.contains(expectedReq)
                                && line.contains(expectedUser)
                                && line.contains(expectedAuction));
                assertTrue(found, "Thiếu dòng log cho " + expectedReq);
            }

            String winnerLog = capturedLogs.stream()
                    .filter(line -> line.contains("status=ACCEPTED"))
                    .findFirst()
                    .orElseThrow();
            String winnerReqId = extractField(winnerLog, "requestId");
            long winnerUserId = Long.parseLong(extractField(winnerLog, "userId"));

            assertFalse(bidRepo.findByRequest(fixture.auctionId(), winnerUserId, winnerReqId).isEmpty());
        }
    }

    private static BidWriteResult executeWithAuditLog(JdbcBidRepository bidRepo,
                                                      Logger logger,
                                                      PlaceBidCommand cmd) throws Exception {
        try {
            BidWriteResult result = bidRepo.placeBid(cmd);
            logger.info(String.format(
                    "BID_AUDIT status=ACCEPTED requestId=%s auctionId=%d userId=%d amountCents=%d version=%d",
                    cmd.clientRequestId(), cmd.auctionId(), cmd.bidderId(),
                    result.currentPriceCents(), result.auctionVersion()));
            return result;
        } catch (BidRejectedException ex) {
            logger.warning(String.format(
                    "BID_AUDIT status=REJECTED requestId=%s auctionId=%d userId=%d amountCents=%d errorCode=%s",
                    cmd.clientRequestId(), cmd.auctionId(), cmd.bidderId(),
                    cmd.amountCents(), ex.errorCode().name()));
            throw ex;
        }
    }

    private static String extractField(String logLine, String key) {
        for (String token : logLine.split("\\s+")) {
            if (token.startsWith(key + "=")) {
                return token.substring(key.length() + 1);
            }
        }
        throw new IllegalArgumentException("Missing key: " + key);
    }

    private static Logger attachMemoryHandler(String loggerName, List<String> sink) {
        Logger logger = Logger.getLogger(loggerName);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        for (Handler existing : logger.getHandlers()) {
            logger.removeHandler(existing);
        }
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record != null && record.getMessage() != null) {
                    sink.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private static Connection openDatabase() throws Exception {
        Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        DatabaseInitializer.initialize(connection, databaseRoot(), false);
        return connection;
    }

    private static Fixture createRunningAuction(Connection connection, int bidderCount) throws Exception {
        JdbcUserRepository users = new JdbcUserRepository(connection);
        long sellerId = users.insert("audit-seller", "hash-seller", Role.SELLER);
        List<Long> bidderIds = new ArrayList<>();
        for (int i = 0; i < bidderCount; i++) {
            bidderIds.add(users.insert("audit-bidder-" + i, "hash-" + i, Role.BIDDER));
        }

        long itemId = new JdbcItemRepository(connection).insert(new CreateItemCommand(
                sellerId, "Audit Item", "Week 3 audit fixture", ItemType.CARD, null));
        Instant start = Instant.now().minusSeconds(60);
        long auctionId = new JdbcAuctionRepository(connection).insert(new CreateAuctionCommand(
                itemId, 1_000L, 50L, start, start.plusSeconds(3_600)));

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
            stmt.setString(1, AuctionStatus.RUNNING.name());
            stmt.setLong(2, auctionId);
            stmt.executeUpdate();
        }
        return new Fixture(auctionId, bidderIds);
    }

    private static Path databaseRoot() {
        Path current = Path.of("database");
        return Files.isDirectory(current.resolve("migrations"))
                ? current.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }

    private record Fixture(long auctionId, List<Long> bidderIds) {
    }
}