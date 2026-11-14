package com.btl.team10.server;

import com.btl.team10.common.dto.BidHistoryDto;
import com.btl.team10.common.dto.BidWriteResult;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.PlaceBidCommand;
import com.btl.team10.common.exception.BidRejectedException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import com.btl.team10.common.protocol.ErrorCode;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Week 3 - Concurrency & Multi-Client Bid Tests")
class ConcurrencyBidTest {

    @Test
    @DisplayName("1. 20 client cùng đặt giá ở version 0: chỉ 1 người thắng, 19 người bị CONFLICT")
    void sameVersionRaceAllowsOnlyOneWinnerAndRejectsOthers() throws Exception {
        try (Connection connection = openDatabase()) {
            AuctionFixture fixture = createRunningAuction(connection, 20);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);
            Object dbLock = new Object();

            int clientCount = 20;
            ExecutorService pool = Executors.newFixedThreadPool(clientCount);
            CountDownLatch ready = new CountDownLatch(clientCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(clientCount);

            AtomicInteger acceptedCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            List<Throwable> unexpectedErrors = java.util.Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < clientCount; i++) {
                final long bidderId = fixture.bidderIds().get(i);
                final String requestId = "race-req-" + i;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();

                        synchronized (dbLock) {
                            bidRepo.placeBid(new PlaceBidCommand(
                                    fixture.auctionId(), bidderId, 1_050L, requestId, 0L));
                        }
                        acceptedCount.incrementAndGet();
                    } catch (BidRejectedException ex) {
                        if (ex.errorCode() == ErrorCode.CONFLICT) {
                            conflictCount.incrementAndGet();
                        } else {
                            unexpectedErrors.add(ex);
                        }
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            pool.shutdown();

            assertTrue(unexpectedErrors.isEmpty(), "Không được phát sinh lỗi ngoài dự kiến: " + unexpectedErrors);
            assertEquals(1, acceptedCount.get(), "Chỉ duy nhất 1 client được chấp nhận tại version 0");
            assertEquals(19, conflictCount.get(), "19 client còn lại phải nhận ErrorCode.CONFLICT");

            List<BidHistoryDto> history = bidRepo.findHistory(fixture.auctionId());
            assertEquals(1, history.size(), "Lịch sử đấu giá chỉ được ghi nhận đúng 1 lượt");
            assertEquals(1_050L, history.get(0).amountCents());
        }
    }

    @Test
    @DisplayName("2. 10 client đua giá đồng thời có retry khi CONFLICT: version tăng tuần tự từ 1 đến 10")
    void concurrentBiddersWithRetryAdvanceVersionSequentially() throws Exception {
        try (Connection connection = openDatabase()) {
            AuctionFixture fixture = createRunningAuction(connection, 10);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);
            Object dbLock = new Object();

            int clientCount = 10;
            ExecutorService pool = Executors.newFixedThreadPool(clientCount);
            CountDownLatch ready = new CountDownLatch(clientCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(clientCount);

            AtomicInteger successCount = new AtomicInteger(0);
            List<Throwable> unexpectedErrors = java.util.Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < clientCount; i++) {
                final long bidderId = fixture.bidderIds().get(i);
                final String requestId = "retry-req-" + i;
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();

                        boolean placed = false;
                        int attempts = 0;
                        while (!placed && attempts < 50) {
                            attempts++;
                            long currentVersion;
                            long nextAmount;
                            synchronized (dbLock) {
                                List<BidHistoryDto> history = bidRepo.findHistory(fixture.auctionId());
                                if (history.isEmpty()) {
                                    currentVersion = 0L;
                                    nextAmount = 1_050L;
                                } else {
                                    BidHistoryDto latest = history.get(history.size() - 1);
                                    currentVersion = latest.auctionVersion();
                                    nextAmount = latest.amountCents() + 50L;
                                }
                                try {
                                    bidRepo.placeBid(new PlaceBidCommand(
                                            fixture.auctionId(), bidderId, nextAmount, requestId, currentVersion));
                                    placed = true;
                                    successCount.incrementAndGet();
                                } catch (BidRejectedException ex) {
                                    if (ex.errorCode() != ErrorCode.CONFLICT) {
                                        throw ex;
                                    }
                                    // Gặp CONFLICT do luồng khác vừa thắng -> lặp lại để đọc version mới
                                }
                            }
                        }
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            ready.await();
            start.countDown();
            done.await();
            pool.shutdown();

            assertTrue(unexpectedErrors.isEmpty(), "Không được có lỗi ngoài dự kiến: " + unexpectedErrors);
            assertEquals(clientCount, successCount.get(), "Cả 10 client đều phải đặt giá thành công sau khi retry");

            List<BidHistoryDto> history = bidRepo.findHistory(fixture.auctionId());
            assertEquals(10, history.size(), "Tổng số lượt đặt giá hợp lệ phải bằng 10");
            assertEquals(10L, history.get(history.size() - 1).auctionVersion(), "Version cuối cùng phải là 10");
            assertEquals(1_500L, history.get(history.size() - 1).amountCents(), "Giá cuối cùng phải là 1500 cents");

            long activeCount = 0;
            long outbidCount = 0;
            for (int i = 0; i < clientCount; i++) {
                BidStatus status = bidRepo.findByRequest(
                                fixture.auctionId(), fixture.bidderIds().get(i), "retry-req-" + i)
                        .orElseThrow().bid().status();
                if (status == BidStatus.ACTIVE) activeCount++;
                if (status == BidStatus.OUTBID) outbidCount++;
            }
            assertEquals(1L, activeCount, "Chỉ người trả giá cao nhất cuối cùng có trạng thái ACTIVE");
            assertEquals(9L, outbidCount, "9 người trước đó phải chuyển sang trạng thái OUTBID");
        }
    }

    @Test
    @DisplayName("3. Gửi trùng lặp cùng một requestId đồng thời (Idempotency): không tạo bản ghi thừa")
    void concurrentDuplicateRequestIsIdempotent() throws Exception {
        try (Connection connection = openDatabase()) {
            AuctionFixture fixture = createRunningAuction(connection, 1);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);
            Object dbLock = new Object();

            int retryThreads = 10;
            ExecutorService pool = Executors.newFixedThreadPool(retryThreads);
            CountDownLatch ready = new CountDownLatch(retryThreads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(retryThreads);

            Set<Long> returnedBidIds = ConcurrentHashMap.newKeySet();
            long bidderId = fixture.bidderIds().get(0);

            for (int i = 0; i < retryThreads; i++) {
                pool.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();

                        BidWriteResult result;
                        synchronized (dbLock) {
                            result = bidRepo.placeBid(new PlaceBidCommand(
                                    fixture.auctionId(), bidderId, 1_050L, "same-client-req-99", 0L));
                        }
                        returnedBidIds.add(result.bid().bidId());
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

            assertEquals(1, returnedBidIds.size(), "Mọi luồng gửi trùng requestId phải nhận cùng 1 bidId");
            assertEquals(1, bidRepo.findHistory(fixture.auctionId()).size(), "Database chỉ ghi 1 dòng lịch sử");
        }
    }

    private static Connection openDatabase() throws Exception {
        Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        DatabaseInitializer.initialize(connection, databaseRoot(), false);
        return connection;
    }

    private static AuctionFixture createRunningAuction(Connection connection, int bidderCount) throws Exception {
        JdbcUserRepository users = new JdbcUserRepository(connection);
        long sellerId = users.insert("conc-seller", "hash-seller", Role.SELLER);
        List<Long> bidderIds = new ArrayList<>();
        for (int i = 0; i < bidderCount; i++) {
            bidderIds.add(users.insert("conc-bidder-" + i, "hash-" + i, Role.BIDDER));
        }

        long itemId = new JdbcItemRepository(connection).insert(new CreateItemCommand(
                sellerId, "Concurrency Item", "Week 3 concurrency test item", ItemType.CARD, null));
        Instant start = Instant.now().minusSeconds(60);
        long auctionId = new JdbcAuctionRepository(connection).insert(new CreateAuctionCommand(
                itemId, 1_000L, 50L, start, start.plusSeconds(3_600)));

        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
            stmt.setString(1, AuctionStatus.RUNNING.name());
            stmt.setLong(2, auctionId);
            stmt.executeUpdate();
        }
        return new AuctionFixture(auctionId, bidderIds);
    }

    private static Path databaseRoot() {
        Path current = Path.of("database");
        return Files.isDirectory(current.resolve("migrations"))
                ? current.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }

    private record AuctionFixture(long auctionId, List<Long> bidderIds) {
    }
}