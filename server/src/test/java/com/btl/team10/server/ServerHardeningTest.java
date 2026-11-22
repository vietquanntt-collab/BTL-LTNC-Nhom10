package com.btl.team10.server;

import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.WalletDto;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.bidding.AntiSnipePolicy;
import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.bidding.SettlementService;
import com.btl.team10.server.network.AuctionSocketServer;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.AutoBidRuleRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.btl.team10.server.repository.SettlementRepository;
import com.btl.team10.server.repository.UserRepository;
import com.btl.team10.server.repository.WalletRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerHardeningTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private AuctionSocketServer server;
    private Thread serverThread;
    private ItemRepository itemRepository;
    private AuctionRepository auctionRepository;
    private WalletRepository walletRepository;
    private AuthService authService;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("week5-hardening.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        DatabaseInitializer.initialize(connection, ServerApplication.resolveDatabaseRoot(), false);

        UserRepository userRepository = new JdbcUserRepository(connection);
        itemRepository = new JdbcItemRepository(connection);
        auctionRepository = new JdbcAuctionRepository(connection);
        AutoBidRuleRepository autoBidRuleRepository = new JdbcAutoBidRuleRepository(connection);
        walletRepository = new JdbcWalletRepository(connection);
        SettlementRepository settlementRepository = new JdbcSettlementRepository(connection);

        SessionManager sessionManager = new SessionManager();
        authService = new AuthService(userRepository, sessionManager);
        EventPublisher eventPublisher = new EventPublisher();

        PlaceBidService placeBidService = new PlaceBidService(
                connection,
                eventPublisher,
                autoBidRuleRepository,
                walletRepository,
                AntiSnipePolicy.defaultPolicy());
        SettlementService settlementService = new SettlementService(
                connection,
                settlementRepository,
                walletRepository,
                eventPublisher);

        server = new AuctionSocketServer(
                0,
                authService,
                sessionManager,
                itemRepository,
                auctionRepository,
                placeBidService,
                settlementService,
                eventPublisher);

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
                // Closed during tearDown.
            }
        }, "test-auction-server");
        serverThread.setDaemon(true);
        serverThread.start();

        long deadline = System.currentTimeMillis() + 3_000L;
        while (!server.isRunning() || server.boundPort() == 0) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Server did not bind ephemeral port in time");
            }
            Thread.sleep(10L);
        }
        port = server.boundPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (serverThread != null) {
            serverThread.join(2_000L);
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private long createSeller(String account, String password) throws SQLException {
        long userId = authService.registerBidder(account, password);
        connection.createStatement().executeUpdate(
                "UPDATE users SET role = 'SELLER' WHERE user_id = " + userId);
        return userId;
    }

    private long createFundedBidder(String account, String password, long balanceCents) throws SQLException {
        long userId = authService.registerBidder(account, password);
        connection.createStatement().executeUpdate(
                "UPDATE wallets SET balance_cents = " + balanceCents + ", frozen_cents = 0 WHERE user_id = " + userId);
        return userId;
    }

    @Test
    void healthAndErrorMapping_handlesMalformedAndUnauthenticatedRequestsSafely() throws Exception {
        try (TestClient client = new TestClient("127.0.0.1", port)) {
            ResponseEnvelope healthResp = client.send(new RequestEnvelope(
                    "v1", "req-health-1", "HEALTH", null, "{}"));
            assertTrue(healthResp.ok());
            JsonObject healthJson = JsonParser.parseString(healthResp.payloadJson()).getAsJsonObject();
            assertEquals("UP", healthJson.get("status").getAsString());
            assertTrue(healthJson.get("activeConnections").getAsInt() >= 1);

            // Missing action should map cleanly to INVALID_REQUEST instead of INTERNAL_ERROR
            String missingActionRaw = "{\"protocolVersion\":\"v1\",\"requestId\":\"req-bad-1\",\"payloadJson\":\"{}\"}";
            ResponseEnvelope missingActionResp = client.sendRaw(missingActionRaw);
            assertFalse(missingActionResp.ok());
            assertEquals(ErrorCode.INVALID_REQUEST, missingActionResp.errorCode());

            // Unsupported protocol version
            ResponseEnvelope badVersionResp = client.send(new RequestEnvelope(
                    "v0", "req-bad-2", "HEALTH", null, "{}"));
            assertFalse(badVersionResp.ok());
            assertEquals(ErrorCode.INVALID_REQUEST, badVersionResp.errorCode());

            // Unauthenticated protected action
            ResponseEnvelope unauthResp = client.send(new RequestEnvelope(
                    "v1", "req-unauth-1", "LIST_AUCTIONS", null, "{}"));
            assertFalse(unauthResp.ok());
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, unauthResp.errorCode());
        }
    }

    @Test
    void authorizationMatrix_enforcesRoleBoundariesAcrossActions() throws Exception {
        long sellerId = createSeller("hardening_seller", "seller123");
        createFundedBidder("hardening_bidder", "bidder123", 1_000_000L);

        long itemId = itemRepository.insert(new CreateItemCommand(
                sellerId, "Hardening Vase", "Ceramic", ItemType.values()[0], null));
        Instant now = Instant.now();
        long auctionId = auctionRepository.insert(new CreateAuctionCommand(
                itemId, 100_000L, 10_000L, now.minusSeconds(60), now.plusSeconds(3600)));

        try (TestClient bidderClient = new TestClient("127.0.0.1", port);
             TestClient sellerClient = new TestClient("127.0.0.1", port)) {

            String bidderToken = bidderClient.login("hardening_bidder", "bidder123");
            String sellerToken = sellerClient.login("hardening_seller", "seller123");

            // BIDDER cannot create item or call admin endpoint
            ResponseEnvelope bidderCreateItem = bidderClient.send(new RequestEnvelope(
                    "v1", "auth-1", "CREATE_ITEM", bidderToken,
                    "{\"name\":\"Illegal\",\"itemType\":\"" + ItemType.values()[0].name() + "\"}"));
            assertFalse(bidderCreateItem.ok());
            assertEquals(ErrorCode.FORBIDDEN, bidderCreateItem.errorCode());

            ResponseEnvelope bidderAdminUsers = bidderClient.send(new RequestEnvelope(
                    "v1", "auth-2", "ADMIN_LIST_USERS", bidderToken, "{}"));
            assertFalse(bidderAdminUsers.ok());
            assertEquals(ErrorCode.FORBIDDEN, bidderAdminUsers.errorCode());

            // SELLER cannot place bid or configure auto-bid
            ResponseEnvelope sellerBid = sellerClient.send(new RequestEnvelope(
                    "v1", "auth-3", "PLACE_BID", sellerToken,
                    "{\"auctionId\":" + auctionId + ",\"amountCents\":110000}"));
            assertFalse(sellerBid.ok());
            assertEquals(ErrorCode.FORBIDDEN, sellerBid.errorCode());

            ResponseEnvelope sellerAutoBid = sellerClient.send(new RequestEnvelope(
                    "v1", "auth-4", "SET_AUTO_BID", sellerToken,
                    "{\"auctionId\":" + auctionId + ",\"maxAmountCents\":200000,\"incrementCents\":10000}"));
            assertFalse(sellerAutoBid.ok());
            assertEquals(ErrorCode.FORBIDDEN, sellerAutoBid.errorCode());
        }
    }

    @Test
    void stressConcurrentSocketBidding_preservesWalletInvariantsAndMonotonicPrice() throws Exception {
        long sellerId = createSeller("stress_seller", "seller123");
        long itemId = itemRepository.insert(new CreateItemCommand(
                sellerId, "Stress Watch", "Mechanical", ItemType.values()[0], null));
        Instant now = Instant.now();
        long auctionId = auctionRepository.insert(new CreateAuctionCommand(
                itemId, 100_000L, 10_000L, now.minusSeconds(60), now.plusSeconds(3600)));

        int bidderCount = 12;
        List<Long> bidderIds = new ArrayList<>();
        List<String> accounts = new ArrayList<>();
        long initialTotalFunds = 0L;

        for (int i = 0; i < bidderCount; i++) {
            String account = "stress_bidder_" + i;
            long uid = createFundedBidder(account, "pass12345", 5_000_000L);
            bidderIds.add(uid);
            accounts.add(account);
            initialTotalFunds += 5_000_000L;
        }

        ExecutorService pool = Executors.newFixedThreadPool(bidderCount);
        CountDownLatch ready = new CountDownLatch(bidderCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < bidderCount; i++) {
            final int idx = i;
            tasks.add(() -> {
                try (TestClient client = new TestClient("127.0.0.1", port)) {
                    String token = client.login(accounts.get(idx), "pass12345");
                    ready.countDown();
                    assertTrue(startGate.await(5, TimeUnit.SECONDS));

                    for (int round = 1; round <= 3; round++) {
                        long offer = 100_000L + (round * 100_000L) + (idx * 10_000L);
                        ResponseEnvelope resp = client.send(new RequestEnvelope(
                                "v1",
                                "stress-" + idx + "-" + round,
                                "PLACE_BID",
                                token,
                                "{\"auctionId\":" + auctionId + ",\"amountCents\":" + offer + "}"));
                        assertNotNull(resp);
                    }
                    return true;
                }
            });
        }

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(task));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        startGate.countDown();

        for (Future<Boolean> future : futures) {
            assertTrue(future.get(15, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        var finalAuction = auctionRepository.findById(auctionId).orElseThrow();
        assertNotNull(finalAuction.winnerId());
        assertTrue(finalAuction.currentPriceCents() >= 300_000L);

        long finalTotalBalance = 0L;
        long totalFrozen = 0L;
        for (long uid : bidderIds) {
            WalletDto wallet = walletRepository.findByUserId(uid).orElseThrow();
            assertTrue(wallet.frozenCents() >= 0L);
            assertTrue(wallet.balanceCents() >= wallet.frozenCents());
            finalTotalBalance += wallet.balanceCents();
            totalFrozen += wallet.frozenCents();
        }
        assertEquals(initialTotalFunds, finalTotalBalance);
        assertEquals(finalAuction.currentPriceCents(), totalFrozen);
    }

    @Test
    void reconnectAndIdempotency_retainsStateAndReceivesEventsAfterDisconnect() throws Exception {
        long sellerId = createSeller("reconn_seller", "seller123");
        long bidder1Id = createFundedBidder("reconn_bidder", "bidder123", 1_000_000L);
        createFundedBidder("watch_bidder", "bidder123", 1_000_000L);

        long itemId = itemRepository.insert(new CreateItemCommand(
                sellerId, "Reconnect Camera", "Vintage", ItemType.values()[0], null));
        Instant now = Instant.now();
        long auctionId = auctionRepository.insert(new CreateAuctionCommand(
                itemId, 100_000L, 10_000L, now.minusSeconds(60), now.plusSeconds(3600)));

        WalletDto beforeWallet = walletRepository.findByUserId(bidder1Id).orElseThrow();

        String savedToken;
        try (TestClient firstConn = new TestClient("127.0.0.1", port)) {
            savedToken = firstConn.login("reconn_bidder", "bidder123");
            ResponseEnvelope bid1 = firstConn.send(new RequestEnvelope(
                    "v1", "idem-bid-1", "PLACE_BID", savedToken,
                    "{\"auctionId\":" + auctionId + ",\"amountCents\":120000}"));
            assertTrue(bid1.ok());
        }

        Thread.sleep(80L);
        assertEquals(0, server.activeConnections());

        try (TestClient reconnected = new TestClient("127.0.0.1", port);
             TestClient watcher = new TestClient("127.0.0.1", port)) {
            watcher.login("watch_bidder", "bidder123");

            ResponseEnvelope retrySameBid = reconnected.send(new RequestEnvelope(
                    "v1", "idem-bid-1", "PLACE_BID", savedToken,
                    "{\"auctionId\":" + auctionId + ",\"amountCents\":120000}"));
            assertTrue(retrySameBid.ok());

            WalletDto afterRetryWallet = walletRepository.findByUserId(bidder1Id).orElseThrow();
            assertEquals(beforeWallet.frozenCents() + 120_000L, afterRetryWallet.frozenCents());

            ResponseEnvelope nextBid = reconnected.send(new RequestEnvelope(
                    "v1", "idem-bid-2", "PLACE_BID", savedToken,
                    "{\"auctionId\":" + auctionId + ",\"amountCents\":130000}"));
            assertTrue(nextBid.ok());

            String eventFrame = watcher.readLine();
            assertNotNull(eventFrame);
            assertTrue(eventFrame.contains("\"eventType\""));
            assertTrue(eventFrame.contains(String.valueOf(auctionId)));
        }
    }

    @Test
    void gracefulShutdown_closesOpenConnectionsPromptly() throws Exception {
        createFundedBidder("shutdown_bidder", "bidder123", 1_000_000L);
        TestClient hangingClient = new TestClient("127.0.0.1", port);
        hangingClient.login("shutdown_bidder", "bidder123");
        assertEquals(1, server.activeConnections());

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            server.stop();
            serverThread.join(2_000L);
        });
        assertFalse(server.isRunning());
        hangingClient.close();
    }

    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final PrintWriter writer;
        private final List<String> receivedFrames = new ArrayList<>();

        private TestClient(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(5_000);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        private String login(String account, String password) throws IOException {
            ResponseEnvelope resp = send(new RequestEnvelope(
                    "v1", "login-" + account, "LOGIN", null,
                    "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}"));
            assertTrue(resp.ok(), "Login failed: " + resp.message());
            return resp.sessionId();
        }

        private ResponseEnvelope send(RequestEnvelope request) throws IOException {
            return sendRaw(JsonCodec.toJson(request));
        }

        private ResponseEnvelope sendRaw(String rawLine) throws IOException {
            writer.println(rawLine);
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("Server closed connection unexpectedly");
                }
                receivedFrames.add(line);
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                if (obj.has("ok") && obj.has("requestId")) {
                    return JsonCodec.fromJson(line, ResponseEnvelope.class);
                }
            }
        }

        private List<String> receivedFrames() {
            return receivedFrames;
        }

        private String readLine() throws IOException {
            String line = reader.readLine();
            if (line != null) {
                receivedFrames.add(line);
            }
            return line;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}