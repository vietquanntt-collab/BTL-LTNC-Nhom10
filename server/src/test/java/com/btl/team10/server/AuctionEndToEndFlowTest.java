package com.btl.team10.server;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.bidding.AntiSnipePolicy;
import com.btl.team10.server.bidding.AuctionExpirationScheduler;
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
import com.btl.team10.server.session.PasswordHasher;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuctionEndToEndFlowTest {
    @TempDir
    Path tempDir;

    private Connection connection;
    private EventPublisher eventPublisher;
    private AuctionSocketServer server;
    private AuctionExpirationScheduler scheduler;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("e2e-auction.db");
        connection = DatabaseConnectionFactory.open(dbFile);
        Path migrationsRoot = ServerApplication.resolveDatabaseRoot();
        DatabaseInitializer.initialize(connection, migrationsRoot, false);

        insertUser(1L, "seller1", "pass123", "SELLER", 1_000_000L);
        insertUser(2L, "bidder1", "pass123", "BIDDER", 500_000L);
        insertUser(3L, "bidder2", "pass123", "BIDDER", 500_000L);

        UserRepository userRepository = new JdbcUserRepository(connection);
        ItemRepository itemRepository = new JdbcItemRepository(connection);
        AuctionRepository auctionRepository = new JdbcAuctionRepository(connection);
        AutoBidRuleRepository autoBidRuleRepository = new JdbcAutoBidRuleRepository(connection);
        WalletRepository walletRepository = new JdbcWalletRepository(connection);
        SettlementRepository settlementRepository = new JdbcSettlementRepository(connection);

        SessionManager sessionManager = new SessionManager();
        AuthService authService = new AuthService(userRepository, sessionManager);
        eventPublisher = new EventPublisher();

        PlaceBidService placeBidService = new PlaceBidService(
                connection,
                eventPublisher,
                autoBidRuleRepository,
                walletRepository,
                new AntiSnipePolicy(45L, 25L, 2));
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
        scheduler = new AuctionExpirationScheduler(
                connection,
                eventPublisher,
                server.databaseLock(),
                settlementService);

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception ignored) {
            }
        }, "e2e-test-server");
        serverThread.setDaemon(true);
        serverThread.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (server.boundPort() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(server.boundPort() > 0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (server != null) {
            server.stop();
        }
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void fullAuctionLifecycle_loginCreateBidExtendAndClose_succeedsEndToEnd() throws Exception {
        int port = server.boundPort();
        List<EventEnvelope> sellerEvents = new ArrayList<>();
        List<EventEnvelope> bidder1Events = new ArrayList<>();
        List<EventEnvelope> bidder2Events = new ArrayList<>();

        try (Socket sellerSocket = new Socket("127.0.0.1", port);
             Socket bidder1Socket = new Socket("127.0.0.1", port);
             Socket bidder2Socket = new Socket("127.0.0.1", port)) {

            sellerSocket.setSoTimeout(3000);
            bidder1Socket.setSoTimeout(3000);
            bidder2Socket.setSoTimeout(3000);

            BufferedReader sellerIn = new BufferedReader(new InputStreamReader(sellerSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter sellerOut = new PrintWriter(sellerSocket.getOutputStream(), true, StandardCharsets.UTF_8);

            BufferedReader bidder1In = new BufferedReader(new InputStreamReader(bidder1Socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter bidder1Out = new PrintWriter(bidder1Socket.getOutputStream(), true, StandardCharsets.UTF_8);

            BufferedReader bidder2In = new BufferedReader(new InputStreamReader(bidder2Socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter bidder2Out = new PrintWriter(bidder2Socket.getOutputStream(), true, StandardCharsets.UTF_8);

            ResponseEnvelope sellerLogin = sendAndReceive(sellerOut, sellerIn, sellerEvents,
                    "req-login-s1", null, "LOGIN", loginPayload("seller1", "pass123"));
            assertTrue(sellerLogin.ok());
            String sellerToken = sellerLogin.sessionId();

            ResponseEnvelope bidder1Login = sendAndReceive(bidder1Out, bidder1In, bidder1Events,
                    "req-login-b1", null, "LOGIN", loginPayload("bidder1", "pass123"));
            assertTrue(bidder1Login.ok());
            String bidder1Token = bidder1Login.sessionId();

            ResponseEnvelope bidder2Login = sendAndReceive(bidder2Out, bidder2In, bidder2Events,
                    "req-login-b2", null, "LOGIN", loginPayload("bidder2", "pass123"));
            assertTrue(bidder2Login.ok());
            String bidder2Token = bidder2Login.sessionId();

            JsonObject itemPayload = new JsonObject();
            itemPayload.addProperty("name", "Holographic Charizard");
            itemPayload.addProperty("description", "Mint condition");
            itemPayload.addProperty("itemType", "CARD");
            ResponseEnvelope createItemResp = sendAndReceive(sellerOut, sellerIn, sellerEvents,
                    "req-item-1", sellerToken, "CREATE_ITEM", itemPayload);
            assertTrue(createItemResp.ok());
            long itemId = JsonParser.parseString(createItemResp.payloadJson()).getAsJsonObject().get("itemId").getAsLong();

            JsonObject auctionPayload = new JsonObject();
            auctionPayload.addProperty("itemId", itemId);
            auctionPayload.addProperty("startPriceCents", 100_000L);
            auctionPayload.addProperty("minIncrementCents", 10_000L);
            auctionPayload.addProperty("durationHours", 1L);
            ResponseEnvelope createAuctionResp = sendAndReceive(sellerOut, sellerIn, sellerEvents,
                    "req-auc-1", sellerToken, "CREATE_AUCTION", auctionPayload);
            assertTrue(createAuctionResp.ok());
            long auctionId = JsonParser.parseString(createAuctionResp.payloadJson()).getAsJsonObject().get("auctionId").getAsLong();

            ResponseEnvelope bid1Resp = sendAndReceive(bidder1Out, bidder1In, bidder1Events,
                    "req-bid-1", bidder1Token, "PLACE_BID", bidPayload(auctionId, 110_000L));
            assertTrue(bid1Resp.ok());

            ResponseEnvelope bid2Resp = sendAndReceive(bidder2Out, bidder2In, bidder2Events,
                    "req-bid-2", bidder2Token, "PLACE_BID", bidPayload(auctionId, 120_000L));
            assertTrue(bid2Resp.ok());

            JsonObject listBidsPayload = new JsonObject();
            listBidsPayload.addProperty("auctionId", auctionId);
            ResponseEnvelope syncOutbidResp = sendAndReceive(bidder1Out, bidder1In, bidder1Events,
                    "req-sync-b1", bidder1Token, "LIST_BIDS", listBidsPayload);
            assertTrue(syncOutbidResp.ok());
            assertTrue(bidder1Events.stream().anyMatch(e -> e.eventType() == EventType.OUTBID));

            synchronized (server.databaseLock()) {
                try (PreparedStatement ps = connection.prepareStatement("UPDATE auctions SET end_at = ? WHERE auction_id = ?")) {
                    ps.setString(1, Instant.now().plusSeconds(15).toString());
                    ps.setLong(2, auctionId);
                    ps.executeUpdate();
                }
            }

            ResponseEnvelope snipeBidResp = sendAndReceive(bidder1Out, bidder1In, bidder1Events,
                    "req-bid-3", bidder1Token, "PLACE_BID", bidPayload(auctionId, 140_000L));
            assertTrue(snipeBidResp.ok());

            ResponseEnvelope syncExtendResp = sendAndReceive(bidder1Out, bidder1In, bidder1Events,
                    "req-sync-b2", bidder1Token, "LIST_BIDS", listBidsPayload);
            assertTrue(syncExtendResp.ok());
            assertTrue(bidder1Events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_EXTENDED));
            assertEquals(1, getExtensionCount(auctionId));

            synchronized (server.databaseLock()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE auctions SET start_at = ?, end_at = ? WHERE auction_id = ?")) {
                    ps.setString(1, Instant.now().minusSeconds(60).toString());
                    ps.setString(2, Instant.now().minusSeconds(5).toString());
                    ps.setLong(3, auctionId);
                    ps.executeUpdate();
                }
            }

            scheduler.closeExpiredAuctions();

            while (bidder1Events.stream().noneMatch(e -> e.eventType() == EventType.AUCTION_SETTLED)) {
                String line = bidder1In.readLine();
                assertNotNull(line);
                bidder1Events.add(JsonCodec.fromJson(line, EventEnvelope.class));
            }

            assertTrue(bidder1Events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_FINISHED));
            assertTrue(bidder1Events.stream().anyMatch(e -> e.eventType() == EventType.AUCTION_SETTLED));

            assertEquals("PAID", getAuctionStatus(auctionId));
            assertEquals(2L, getWinnerId(auctionId));
            assertEquals(360_000L, getBalanceCents(2L));
            assertEquals(0L, getFrozenCents(2L));
            assertEquals(500_000L, getBalanceCents(3L));
            assertEquals(1_140_000L, getBalanceCents(1L));

            sellerOut.println("QUIT");
            bidder1Out.println("QUIT");
            bidder2Out.println("QUIT");
        }
    }

    private ResponseEnvelope sendAndReceive(PrintWriter writer, BufferedReader reader,
                                            List<EventEnvelope> events, String requestId,
                                            String sessionId, String action, JsonObject payload) throws Exception {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("protocolVersion", "v1");
        envelope.addProperty("requestId", requestId);
        envelope.addProperty("action", action);
        if (sessionId != null) {
            envelope.addProperty("sessionId", sessionId);
        }
        envelope.addProperty("payloadJson", payload.toString());
        writer.println(JsonCodec.toJson(envelope));

        while (true) {
            String line = reader.readLine();
            assertNotNull(line);
            JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            if (!obj.has("ok") && !obj.has("action")) {
                events.add(JsonCodec.fromJson(line, EventEnvelope.class));
            } else {
                return JsonCodec.fromJson(line, ResponseEnvelope.class);
            }
        }
    }

    private static JsonObject loginPayload(String account, String password) {
        JsonObject payload = new JsonObject();
        payload.addProperty("account", account);
        payload.addProperty("password", password);
        return payload;
    }

    private static JsonObject bidPayload(long auctionId, long amountCents) {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("amountCents", amountCents);
        return payload;
    }

    private void insertUser(long id, String account, String rawPassword, String role, long balance) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (user_id, account, password_hash, role) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, account);
            ps.setString(3, PasswordHasher.hash(rawPassword));
            ps.setString(4, role);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO wallets (user_id, balance_cents, frozen_cents) VALUES (?, ?, 0)")) {
            ps.setLong(1, id);
            ps.setLong(2, balance);
            ps.executeUpdate();
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

    private String getAuctionStatus(long auctionId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("SELECT status FROM auctions WHERE auction_id = ?")) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
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
