package com.btl.team10.server;

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
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditHardeningRegressionTest {
    private Connection connection;
    private EventPublisher events;
    private PlaceBidService placeBidService;
    private SettlementService settlementService;
    private AuctionSocketServer server;
    private Thread serverThread;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        Path root = Files.isDirectory(Path.of("database/migrations"))
                ? Path.of("database") : Path.of("..", "database");
        DatabaseInitializer.initialize(connection, root, true);
        var users = new JdbcUserRepository(connection);
        var sessions = new SessionManager();
        events = new EventPublisher();
        placeBidService = new PlaceBidService(
                connection, events,
                new JdbcAutoBidRuleRepository(connection),
                new JdbcWalletRepository(connection),
                new AntiSnipePolicy(30L, 30L, 5));
        settlementService = new SettlementService(
                connection,
                new JdbcSettlementRepository(connection),
                new JdbcWalletRepository(connection),
                events);
        server = new AuctionSocketServer(
                0, new AuthService(users, sessions), sessions,
                new JdbcItemRepository(connection), new JdbcAuctionRepository(connection),
                placeBidService, settlementService, events);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception ignored) {
                // Server stopped during tearDown.
            }
        }, "audit-regression-server");
        serverThread.setDaemon(true);
        serverThread.start();
        for (int attempt = 0; attempt < 100 && server.boundPort() == 0; attempt++) {
            Thread.sleep(10);
        }
        port = server.boundPort();
        assertTrue(port > 0);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        serverThread.join(2000);
        connection.close();
    }

    @Test
    void requestReceiptNeverLeaksAnotherUsersSessionOrCollidesAcrossActions() throws Exception {
        try (Peer victim = new Peer(); Peer attacker = new Peer()) {
            String victimToken = victim.login("bidder1", "bidder123");
            String sharedReqId = "shared-client-req-123";

            ResponseEnvelope victimBid = victim.sendWithId(sharedReqId, "PLACE_BID", victimToken,
                    obj("auctionId", 1, "amountCents", 115000, "clientRequestId", sharedReqId));
            assertTrue(victimBid.ok(), victimBid.message());

            assertTrue(attacker.send("REGISTER", null,
                    obj("account", "attacker1", "password", "secret123")).ok());
            String attackerToken = attacker.login("attacker1", "secret123");

            ResponseEnvelope hijackAttempt = attacker.sendWithId(sharedReqId, "PLACE_BID", attackerToken,
                    obj("auctionId", 1, "amountCents", 120000, "clientRequestId", sharedReqId));
            assertFalse(hijackAttempt.ok(), "Attacker must not replay victim receipt");
            assertEquals(ErrorCode.INSUFFICIENT_BALANCE, hijackAttempt.errorCode());
            assertNotEquals(victimToken, hijackAttempt.sessionId());

            ResponseEnvelope victimAuto = victim.sendWithId(sharedReqId, "SET_AUTO_BID", victimToken,
                    obj("auctionId", 1, "maxAmountCents", 200000, "incrementCents", 5000,
                            "clientRequestId", sharedReqId));
            assertTrue(victimAuto.ok(), "SET_AUTO_BID must not collide with PLACE_BID receipt");
            assertEquals("SET_AUTO_BID", victimAuto.action());
        }
    }

    @Test
    void overflowValuesInCreateAuctionAndSetAutoBidAreRejectedSafely() throws Exception {
        try (Peer seller = new Peer(); Peer bidder1 = new Peer(); Peer bidder2 = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String bidder1Token = bidder1.login("bidder1", "bidder123");
            String bidder2Token = bidder2.login("bidder2", "bidder123");

            long itemId = createItem(seller, sellerToken, "Overflow item");
            ResponseEnvelope badAuction = seller.send("CREATE_AUCTION", sellerToken,
                    obj("itemId", itemId, "startPriceCents", Long.MAX_VALUE,
                            "minIncrementCents", 5000, "durationHours", 1));
            assertFalse(badAuction.ok());
            assertEquals(ErrorCode.VALIDATION_ERROR, badAuction.errorCode());

            ResponseEnvelope badAuto = bidder2.send("SET_AUTO_BID", bidder2Token,
                    obj("auctionId", 1, "maxAmountCents", Long.MAX_VALUE,
                            "incrementCents", Long.MAX_VALUE));
            assertFalse(badAuto.ok());
            assertEquals(ErrorCode.VALIDATION_ERROR, badAuto.errorCode());

            ResponseEnvelope tinyAuto = bidder1.send("SET_AUTO_BID", bidder1Token,
                    obj("auctionId", 1, "maxAmountCents", 1, "incrementCents", 1));
            assertFalse(tinyAuto.ok());

            assertTrue(bidder1.send("PLACE_BID", bidder1Token,
                    obj("auctionId", 1, "amountCents", 115000)).ok());
        }
    }

    @Test
    void autoBidTieBreakAndNarrowCapGapAlwaysFavorHigherPriorityBidder() throws Exception {
        try (Peer seller = new Peer(); Peer bidder1 = new Peer(); Peer bidder2 = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String b1Token = bidder1.login("bidder1", "bidder123");
            String b2Token = bidder2.login("bidder2", "bidder123");

            long a1 = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Tie Item"), 110000, 5000);
            assertTrue(bidder1.send("SET_AUTO_BID", b1Token,
                    obj("auctionId", a1, "maxAmountCents", 120000, "incrementCents", 5000)).ok());
            assertEquals(115000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a1));
            assertTrue(bidder2.send("SET_AUTO_BID", b2Token,
                    obj("auctionId", a1, "maxAmountCents", 120000, "incrementCents", 5000)).ok());
            assertEquals(120000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a1));
            assertEquals(3L, scalarLong("SELECT winner_id FROM auctions WHERE auction_id = " + a1),
                    "Earlier equal-cap auto-bidder must win");

            long a2 = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Gap Item"), 110000, 5000);
            assertTrue(bidder1.send("SET_AUTO_BID", b1Token,
                    obj("auctionId", a2, "maxAmountCents", 123000, "incrementCents", 5000)).ok());
            assertTrue(bidder2.send("SET_AUTO_BID", b2Token,
                    obj("auctionId", a2, "maxAmountCents", 120000, "incrementCents", 5000)).ok());
            assertEquals(123000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a2));
            assertEquals(3L, scalarLong("SELECT winner_id FROM auctions WHERE auction_id = " + a2),
                    "Higher-cap auto-bidder must win narrow gap");
        }
    }

    @Test
    void autoBidCapsCompetitorByRealWalletBalanceAndClampsCustomIncrementToMaxBid() throws Exception {
        try (Peer seller = new Peer(); Peer poor = new Peer(); Peer b1 = new Peer(); Peer b2 = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String b1Token = b1.login("bidder1", "bidder123");
            String b2Token = b2.login("bidder2", "bidder123");

            assertTrue(poor.send("REGISTER", null, obj("account", "poorbidder", "password", "pass1234")).ok());
            String poorToken = poor.login("poorbidder", "pass1234");
            long poorId = scalarLong("SELECT user_id FROM users WHERE account = 'poorbidder'");
            synchronized (server.databaseLock()) {
                try (var st = connection.createStatement()) {
                    st.executeUpdate("UPDATE wallets SET balance_cents = 20000 WHERE user_id = " + poorId);
                }
            }

            // BL-01: Phantom-Max Overpay must be prevented
            long a1 = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Phantom"), 10000, 5000);
            assertTrue(poor.send("SET_AUTO_BID", poorToken,
                    obj("auctionId", a1, "maxAmountCents", 1000000, "incrementCents", 5000)).ok());
            assertTrue(b2.send("SET_AUTO_BID", b2Token,
                    obj("auctionId", a1, "maxAmountCents", 500000, "incrementCents", 5000)).ok());
            assertEquals(4L, scalarLong("SELECT winner_id FROM auctions WHERE auction_id = " + a1));
            assertEquals(25000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a1),
                    "Competitor must pay real affordable cap + increment, not phantom max");

            // BL-02: Auto-Bid with large custom increment must clamp to maxBid instead of giving up
            long a2 = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Clamp"), 100000, 5000);
            assertTrue(b1.send("SET_AUTO_BID", b1Token,
                    obj("auctionId", a2, "maxAmountCents", 200000, "incrementCents", 50000)).ok());
            assertEquals(150000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a2));

            // BL-05: Manual bid immediately outbid by Auto-Bid must report OUTBID / leading=false
            ResponseEnvelope outbidResp = b2.send("PLACE_BID", b2Token,
                    obj("auctionId", a2, "amountCents", 160000));
            assertTrue(outbidResp.ok());
            JsonObject payload = JsonParser.parseString(outbidResp.payloadJson()).getAsJsonObject();
            assertEquals("OUTBID", payload.get("status").getAsString());
            assertFalse(payload.get("leading").getAsBoolean());
            assertEquals(200000L, payload.get("currentPriceCents").getAsLong());
            assertEquals(3L, scalarLong("SELECT winner_id FROM auctions WHERE auction_id = " + a2));
            assertEquals(200000L, scalarLong("SELECT current_price_cents FROM auctions WHERE auction_id = " + a2));
        }
    }

    @Test
    void unauthenticatedAndLoggedOutSocketsReceiveNoBroadcastsAndPipeListRequiresAuth() throws Exception {
        try (Peer unauth = new Peer(); Peer loggedOut = new Peer(); Peer bidder = new Peer()) {
            String outToken = loggedOut.login("bidder2", "bidder123");
            assertTrue(loggedOut.send("LOGOUT", outToken, obj()).ok());

            String pipeResp = unauth.sendRawLine("LIST_AUCTIONS");
            assertTrue(pipeResp.startsWith("ERROR|"), "Unauthenticated pipe LIST_AUCTIONS must be rejected");

            String bidderToken = bidder.login("bidder1", "bidder123");
            assertTrue(bidder.send("PLACE_BID", bidderToken, obj("auctionId", 1, "amountCents", 115000)).ok());

            unauth.assertNoEventReceived();
            loggedOut.assertNoEventReceived();
        }
    }

    @Test
    void antiSnipeDoesNotDoubleExtendWithAutoBidOrSelfOutbidAndCancelRejectsExpired() throws Exception {
        AntiSnipePolicy policy = new AntiSnipePolicy(30L, 30L, 5);
        Instant base = Instant.parse("2026-11-26T12:00:00Z");
        assertFalse(policy.shouldExtend(base, base.plusSeconds(30).plusMillis(500), 0),
                "30.5s remaining must be strictly outside 30s window");
        assertTrue(policy.shouldExtend(base, base.plusSeconds(30), 0));

        try (Peer seller = new Peer(); Peer b1 = new Peer(); Peer b2 = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String b1Token = b1.login("bidder1", "bidder123");
            String b2Token = b2.login("bidder2", "bidder123");

            long a1 = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Snipe"), 100000, 5000);
            assertTrue(b1.send("SET_AUTO_BID", b1Token,
                    obj("auctionId", a1, "maxAmountCents", 150000, "incrementCents", 5000)).ok());

            synchronized (server.databaseLock()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE auctions SET end_at = ? WHERE auction_id = ?")) {
                    ps.setString(1, Instant.now().plusMillis(800).toString());
                    ps.setLong(2, a1);
                    ps.executeUpdate();
                }
            }

            assertTrue(b2.send("PLACE_BID", b2Token, obj("auctionId", a1, "amountCents", 110000)).ok());
            assertEquals(1L, scalarLong("SELECT extension_count FROM auctions WHERE auction_id = " + a1),
                    "1 manual bid + immediate Auto-Bid must extend only once");

            // Self-outbid by current leader (bidder1) must not burn Anti-Snipe
            synchronized (server.databaseLock()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE auctions SET end_at = ? WHERE auction_id = ?")) {
                    ps.setString(1, Instant.now().plusSeconds(10).toString());
                    ps.setLong(2, a1);
                    ps.executeUpdate();
                }
            }
            assertTrue(b1.send("PLACE_BID", b1Token, obj("auctionId", a1, "amountCents", 125000)).ok());
            assertEquals(1L, scalarLong("SELECT extension_count FROM auctions WHERE auction_id = " + a1),
                    "Leader self-outbid must not burn Anti-Snipe extensions");

            // BUG-08: CANCEL_AUCTION must fail after end_at has passed
            synchronized (server.databaseLock()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE auctions SET start_at = ?, end_at = ? WHERE auction_id = ?")) {
                    Instant pastEnd = Instant.now().minusSeconds(2);
                    ps.setString(1, pastEnd.minusSeconds(3600).toString());
                    ps.setString(2, pastEnd.toString());
                    ps.setLong(3, a1);
                    ps.executeUpdate();
                }
            }
            ResponseEnvelope lateCancel = seller.send("CANCEL_AUCTION", sellerToken, obj("auctionId", a1));
            assertFalse(lateCancel.ok());
            assertEquals(ErrorCode.CONFLICT, lateCancel.errorCode());
        }
    }

    @Test
    void cancelledAuctionAllowsEditingAndRelistingItem() throws Exception {
        try (Peer seller = new Peer()) {
            String token = seller.login("seller", "seller123");
            long itemId = createItem(seller, token, "Relistable Plushie");
            long firstAuction = createAuction(seller, token, itemId, 50000, 5000);
            assertTrue(seller.send("CANCEL_AUCTION", token, obj("auctionId", firstAuction)).ok());

            assertTrue(seller.send("UPDATE_ITEM", token,
                    obj("itemId", itemId, "name", "Relistable Plushie v2")).ok());
            long secondAuction = createAuction(seller, token, itemId, 60000, 5000);
            assertTrue(secondAuction > firstAuction);
        }
    }

    private long createItem(Peer peer, String token, String name) throws Exception {
        ResponseEnvelope resp = peer.send("CREATE_ITEM", token,
                obj("name", name, "description", "test", "itemType", "FIGURE"));
        assertTrue(resp.ok(), resp.message());
        return JsonParser.parseString(resp.payloadJson()).getAsJsonObject().get("itemId").getAsLong();
    }

    private long createAuction(Peer peer, String token, long itemId, long start, long inc) throws Exception {
        ResponseEnvelope resp = peer.send("CREATE_AUCTION", token,
                obj("itemId", itemId, "startPriceCents", start, "minIncrementCents", inc, "durationHours", 1));
        assertTrue(resp.ok(), resp.message());
        return JsonParser.parseString(resp.payloadJson()).getAsJsonObject().get("auctionId").getAsLong();
    }

    private long scalarLong(String sql) throws Exception {
        synchronized (server.databaseLock()) {
            try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private static JsonObject obj(Object... pairs) {
        JsonObject out = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            Object v = pairs[i + 1];
            if (v instanceof Number n) {
                out.addProperty(pairs[i].toString(), n);
            } else if (v instanceof Boolean b) {
                out.addProperty(pairs[i].toString(), b);
            } else {
                out.addProperty(pairs[i].toString(), v.toString());
            }
        }
        return out;
    }

    private final class Peer implements AutoCloseable {
        private final Socket socket = new Socket("127.0.0.1", port);
        private final BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        private final PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

        Peer() throws Exception {
            socket.setSoTimeout(3000);
        }

        String login(String account, String password) throws Exception {
            ResponseEnvelope resp = send("LOGIN", null, obj("account", account, "password", password));
            assertTrue(resp.ok(), resp.message());
            return resp.sessionId();
        }

        ResponseEnvelope send(String action, String token, JsonObject payload) throws Exception {
            return sendWithId(UUID.randomUUID().toString(), action, token, payload);
        }

        ResponseEnvelope sendWithId(String reqId, String action, String token, JsonObject payload) throws Exception {
            writer.println(JsonCodec.toJson(new RequestEnvelope("v1", reqId, action, token, payload.toString())));
            for (String line; (line = reader.readLine()) != null;) {
                JsonObject parsed = JsonParser.parseString(line).getAsJsonObject();
                if (parsed.has("eventType")) {
                    continue;
                }
                return JsonCodec.fromJson(line, ResponseEnvelope.class);
            }
            throw new AssertionError("Socket closed before response");
        }

        String sendRawLine(String raw) throws Exception {
            writer.println(raw);
            return reader.readLine();
        }

        void assertNoEventReceived() throws Exception {
            socket.setSoTimeout(250);
            assertThrows(SocketTimeoutException.class, reader::readLine,
                    "Unauthenticated/logged-out socket must not receive broadcast events");
            socket.setSoTimeout(3000);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
