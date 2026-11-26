package com.btl.team10.server.network;

import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.bidding.AuctionExpirationScheduler;
import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.bidding.SettlementService;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.PasswordHasher;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual JSON routes used by the Seller/Admin/registration UI. */
class ManagementSocketIntegrationTest {
    private Connection connection;
    private AuctionSocketServer server;
    private SettlementService settlements;
    private Thread serverThread;
    private int port;

    @BeforeEach
    void startServer() throws Exception {
        connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        Path root = Files.isDirectory(Path.of("database/migrations"))
                ? Path.of("database") : Path.of("..", "database");
        DatabaseInitializer.initialize(connection, root, true);
        var users = new JdbcUserRepository(connection);
        users.insert("seller-other", PasswordHasher.hash("other123"), com.btl.team10.common.model.Role.SELLER);
        var sessions = new SessionManager();
        var events = new EventPublisher();
        settlements = new SettlementService(connection, new JdbcSettlementRepository(connection),
                new JdbcWalletRepository(connection), events);
        server = new AuctionSocketServer(0, new AuthService(users, sessions), sessions,
                new JdbcItemRepository(connection), new JdbcAuctionRepository(connection),
                new PlaceBidService(connection, events), settlements, events);
        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception error) {
                if (!Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException(error);
                }
            }
        }, "management-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        for (int attempt = 0; attempt < 100 && server.boundPort() == 0; attempt++) {
            Thread.sleep(10);
        }
        port = server.boundPort();
        assertTrue(port > 0, "server did not bind");
    }

    @AfterEach
    void stopServer() throws Exception {
        server.stop();
        serverThread.join(2000);
        connection.close();
    }

    @Test
    void registerCreatesHashedBidderAndWalletAndPreservesPasswordWhitespace() throws Exception {
        try (Peer peer = new Peer()) {
            ResponseEnvelope registration = peer.send("REGISTER", null, object(
                    "account", " new-bidder ", "password", " pass123 ", "role", "ADMIN"));
            assertTrue(registration.ok(), registration.message());
            assertEquals("BIDDER", payload(registration).get("role").getAsString());
            String stored = scalarString("SELECT password_hash FROM users WHERE account = 'new-bidder'");
            assertNotEquals(" pass123 ", stored);
            assertTrue(PasswordHasher.verify(" pass123 ", stored));

            ResponseEnvelope login = peer.send("LOGIN", null,
                    object("account", "NEW-BIDDER", "password", " pass123 "));
            assertTrue(login.ok());
            assertEquals(0, payload(login).get("balanceCents").getAsLong());
            assertEquals(0, payload(login).get("frozenCents").getAsLong());
            assertEquals("BIDDER", payload(login).get("role").getAsString());
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, peer.send("LOGIN", null,
                    object("account", "new-bidder", "password", "pass123")).errorCode());
            assertEquals(ErrorCode.CONFLICT, peer.send("REGISTER", null,
                    object("account", "NEW-BIDDER", "password", "secret123")).errorCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, peer.send("REGISTER", null,
                    object("account", "short-pass", "password", "123")).errorCode());
            assertEquals(ErrorCode.FORBIDDEN, peer.send("CREATE_ITEM", login.sessionId(),
                    object("name", "Forbidden", "itemType", "CARD")).errorCode());
        }
    }

    @Test
    void sellerCanListPartiallyUpdateAndDeleteOwnUnlistedItemsWithAdminOverride() throws Exception {
        try (Peer seller = new Peer(); Peer other = new Peer(); Peer admin = new Peer()) {
            String token = seller.login("seller", "seller123");
            String otherToken = other.login("seller-other", "other123");
            String adminToken = admin.login("admin", "admin123");
            long id = createItem(seller, token, "Editable");
            assertEquals(0, array(other.send("LIST_ITEMS", otherToken, object())).size());
            assertEquals(ErrorCode.FORBIDDEN, other.send("UPDATE_ITEM", otherToken,
                    object("itemId", id, "name", "Stolen")).errorCode());
            assertEquals(ErrorCode.FORBIDDEN, other.send("DELETE_ITEM", otherToken,
                    object("itemId", id)).errorCode());
            assertTrue(seller.send("UPDATE_ITEM", token,
                    object("itemId", id, "name", "Renamed")).ok());
            JsonArray items = array(seller.send("LIST_ITEMS", token, object()));
            assertTrue(items.asList().stream().anyMatch(value ->
                    value.getAsJsonObject().get("name").getAsString().equals("Renamed")));
            assertEquals("CARD", scalarString("SELECT item_type FROM items WHERE item_id = " + id));
            assertEquals("fixture", scalarString("SELECT description FROM items WHERE item_id = " + id));
            assertTrue(admin.send("UPDATE_ITEM", adminToken,
                    object("itemId", id, "description", "Reviewed")).ok());
            assertTrue(admin.send("DELETE_ITEM", adminToken, object("itemId", id)).ok());
            assertEquals(ErrorCode.NOT_FOUND, seller.send("DELETE_ITEM", token, object("itemId", id)).errorCode());
        }
    }

    @Test
    void listedItemCannotBeChangedOrDeletedAndDuplicateAuctionIsAConflict() throws Exception {
        try (Peer seller = new Peer()) {
            String token = seller.login("seller", "seller123");
            long itemId = createItem(seller, token, "Immutable listing");
            createAuction(seller, token, itemId);
            assertEquals(ErrorCode.CONFLICT, seller.send("UPDATE_ITEM", token,
                    object("itemId", itemId, "name", "Changed after listing")).errorCode());
            assertEquals(ErrorCode.CONFLICT, seller.send("DELETE_ITEM", token, object("itemId", itemId)).errorCode());
            assertEquals(ErrorCode.CONFLICT, seller.send("CREATE_AUCTION", token, object("itemId", itemId,
                    "startPriceCents", 1000, "minIncrementCents", 100, "durationHours", 1)).errorCode());
            assertEquals("Immutable listing", scalarString("SELECT name FROM items WHERE item_id = " + itemId));
        }
    }

    @Test
    void filtersPaginationAndAdminUserListRespectTheContract() throws Exception {
        try (Peer seller = new Peer(); Peer admin = new Peer(); Peer bidder = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String adminToken = admin.login("admin", "admin123");
            String bidderToken = bidder.login("bidder1", "bidder123");
            long id = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Filter"));
            assertTrue(admin.send("CANCEL_AUCTION", adminToken, object("auctionId", id)).ok());
            assertEquals(1, array(bidder.send("LIST_AUCTIONS", bidderToken, object())).size());
            assertEquals(2, array(bidder.send("LIST_AUCTIONS", bidderToken, object("status", "ALL"))).size());
            assertEquals(1, array(bidder.send("LIST_AUCTIONS", bidderToken, object("status", "cancelled"))).size());
            assertEquals(1, array(bidder.send("LIST_AUCTIONS", bidderToken,
                    object("status", "ALL", "page", 2, "pageSize", 1))).size());
            assertEquals(0, array(bidder.send("LIST_AUCTIONS", bidderToken,
                    object("status", "ALL", "page", Long.MAX_VALUE, "pageSize", Long.MAX_VALUE))).size());
            assertEquals(ErrorCode.VALIDATION_ERROR, bidder.send("LIST_AUCTIONS", bidderToken,
                    object("page", 1.2)).errorCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, bidder.send("LIST_AUCTIONS", bidderToken,
                    object("status", "CLOSED")).errorCode());
            assertEquals(ErrorCode.FORBIDDEN, bidder.send("ADMIN_LIST_USERS", bidderToken, object()).errorCode());
            ResponseEnvelope users = admin.send("ADMIN_LIST_USERS", adminToken, object("role", "BIDDER"));
            assertEquals(2, array(users).size());
            assertFalse(users.payloadJson().contains("password"));
            assertEquals(1, array(admin.send("ADMIN_LIST_USERS", adminToken, object("account", "bidder1"))).size());
        }
    }

    @Test
    void cancellingAuctionReleasesOnlyItsHoldAndPublishesAfterTheResponse() throws Exception {
        try (Peer seller = new Peer(); Peer bidder = new Peer(); Peer other = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String bidderToken = bidder.login("bidder1", "bidder123");
            String otherToken = other.login("seller-other", "other123");
            long id = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Cancel"));
            assertTrue(bidder.send("SET_AUTO_BID", bidderToken, object("auctionId", id,
                    "maxAmountCents", 2000, "incrementCents", 100)).ok());
            assertEquals(1100, scalarLong("SELECT frozen_cents FROM wallets WHERE user_id = 3"));
            assertEquals(ErrorCode.FORBIDDEN, other.send("CANCEL_AUCTION", otherToken,
                    object("auctionId", id)).errorCode());
            assertEquals(ErrorCode.FORBIDDEN, bidder.send("CANCEL_AUCTION", bidderToken,
                    object("auctionId", id)).errorCode());
            ResponseEnvelope cancelled = seller.send("CANCEL_AUCTION", sellerToken, object("auctionId", id));
            assertTrue(cancelled.ok(), cancelled.message());
            EventEnvelope event = seller.readEvent(EventType.AUCTION_CANCELLED);
            assertEquals(id, event.auctionId());
            bidder.readEvent(EventType.AUCTION_CANCELLED);
            bidder.readEvent(EventType.BALANCE_CHANGED);
            assertEquals(0, scalarLong("SELECT frozen_cents FROM wallets WHERE user_id = 3"));
            assertEquals(110000, scalarLong("SELECT frozen_cents FROM wallets WHERE user_id = 4"),
                    "the unrelated demo auction hold must remain");
            assertEquals(2000000, scalarLong("SELECT balance_cents FROM wallets WHERE user_id = 3"));
            assertEquals(0, scalarLong("SELECT COUNT(*) FROM auto_bid_rules WHERE auction_id = " + id + " AND active = 1"));
            assertEquals(0, scalarLong("SELECT COUNT(*) FROM bids WHERE auction_id = " + id + " AND status = 'ACTIVE'"));
            assertEquals(0, scalarLong("SELECT COUNT(*) FROM settlements WHERE auction_id = " + id));
            long version = scalarLong("SELECT version FROM auctions WHERE auction_id = " + id);
            assertEquals(ErrorCode.CONFLICT, seller.send("CANCEL_AUCTION", sellerToken,
                    object("auctionId", id)).errorCode());
            assertEquals(version, scalarLong("SELECT version FROM auctions WHERE auction_id = " + id));
            assertEquals(1, scalarLong("SELECT COUNT(*) FROM auction_events WHERE auction_id = " + id
                    + " AND event_type = 'AUCTION_CANCELLED'"));
            assertEquals(ErrorCode.AUCTION_NOT_OPEN, bidder.send("PLACE_BID", bidderToken,
                    object("auctionId", id, "amountCents", 1200)).errorCode());
        }
    }

    @Test
    void schedulerSettlesNoBidAuctionAndRetriesFailedTransfersWithoutDoublePayment() throws Exception {
        try (Peer seller = new Peer(); Peer bidder = new Peer()) {
            String sellerToken = seller.login("seller", "seller123");
            String bidderToken = bidder.login("bidder1", "bidder123");
            long emptyId = createAuction(seller, sellerToken, createItem(seller, sellerToken, "No bid"));
            long wonId = createAuction(seller, sellerToken, createItem(seller, sellerToken, "Retry"));
            assertTrue(bidder.send("PLACE_BID", bidderToken, object("auctionId", wonId, "amountCents", 1100)).ok());
            AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(connection,
                    new EventPublisher(), server.databaseLock(), settlements);
            synchronized (server.databaseLock()) {
                setEnd(emptyId, Instant.now().minusSeconds(1));
                setEnd(wonId, Instant.now().minusSeconds(1));
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("UPDATE wallets SET frozen_cents = 0 WHERE user_id = 3");
                }
                assertThrows(Exception.class, scheduler::closeExpiredAuctions);
                assertEquals("PAID", scalarString("SELECT status FROM auctions WHERE auction_id = " + emptyId));
                assertEquals(0, scalarLong("SELECT winning_amount_cents FROM settlements WHERE auction_id = " + emptyId));
                assertEquals("FINISHED", scalarString("SELECT status FROM auctions WHERE auction_id = " + wonId));
                assertEquals(0, scalarLong("SELECT COUNT(*) FROM settlements WHERE auction_id = " + wonId));
                assertEquals(0, scalarLong("SELECT COUNT(*) FROM auction_events WHERE auction_id = " + wonId
                        + " AND event_type = 'AUCTION_SETTLED'"));
                try (var sql = connection.createStatement()) {
                    sql.executeUpdate("UPDATE wallets SET frozen_cents = 1100 WHERE user_id = 3");
                }
                scheduler.closeExpiredAuctions();
                scheduler.closeExpiredAuctions();
                assertEquals("PAID", scalarString("SELECT status FROM auctions WHERE auction_id = " + wonId));
                assertEquals(1998900, scalarLong("SELECT balance_cents FROM wallets WHERE user_id = 3"));
                assertEquals(1100, scalarLong("SELECT balance_cents FROM wallets WHERE user_id = 2"));
                assertEquals(1, scalarLong("SELECT COUNT(*) FROM auction_events WHERE auction_id = " + wonId
                        + " AND event_type = 'AUCTION_SETTLED'"));
            }
            scheduler.stop();
        }
    }

    @Test
    void unauthenticatedAndMalformedRequestsReturnJsonAndKeepTheConnectionUsable() throws Exception {
        try (Peer peer = new Peer()) {
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, peer.send("LIST_ITEMS", null, object()).errorCode());
            String token = peer.login("seller", "seller123");
            assertEquals(ErrorCode.VALIDATION_ERROR, peer.send("UPDATE_ITEM", token,
                    object("itemId", -1)).errorCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, peer.send("CREATE_ITEM", token,
                    object("name", "Bad", "itemType", "OTHER")).errorCode());
            assertEquals(ErrorCode.VALIDATION_ERROR, peer.send("CREATE_AUCTION", token,
                    object("itemId", 1, "startPriceCents", 1000.2,
                            "minIncrementCents", 100, "durationHours", 1)).errorCode());
            assertTrue(peer.send("LOGOUT", token, object()).ok());
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, peer.send("LIST_ITEMS", token, object()).errorCode());
            assertTrue(peer.send("LOGIN", null, object("account", "seller", "password", "seller123")).ok());
        }
    }

    @Test
    void sessionResumeRefreshesWalletAndRebindsTargetedEvents() throws Exception {
        String token;
        try (Peer original = new Peer()) { token = original.login("bidder2", "bidder123"); }
        try (Peer resumed = new Peer(); Peer bidder = new Peer()) {
            var wallet = resumed.send("SESSION", token, object());
            assertTrue(wallet.ok());
            assertEquals(110000, payload(wallet).get("frozenCents").getAsLong());
            assertEquals(2000000, payload(wallet).get("balanceCents").getAsLong());
            assertEquals(ErrorCode.AUTHENTICATION_FAILED, resumed.send("SESSION", "expired-token", object()).errorCode());
            String bidderToken = bidder.login("bidder1", "bidder123");
            assertTrue(bidder.send("PLACE_BID", bidderToken, object("auctionId", 1, "amountCents", 115000)).ok());
            var event = resumed.readEvent(EventType.BALANCE_CHANGED);
            assertEquals(0, JsonParser.parseString(event.payloadJson()).getAsJsonObject().get("frozenCents").getAsLong());
        }
    }

    private long createItem(Peer peer, String token, String name) throws Exception {
        ResponseEnvelope result = peer.send("CREATE_ITEM", token,
                object("name", name, "description", "fixture", "itemType", "CARD"));
        assertTrue(result.ok(), result.message());
        return payload(result).get("itemId").getAsLong();
    }

    private long createAuction(Peer peer, String token, long itemId) throws Exception {
        ResponseEnvelope result = peer.send("CREATE_AUCTION", token, object("itemId", itemId,
                "startPriceCents", 1000, "minIncrementCents", 100, "durationHours", 1));
        assertTrue(result.ok(), result.message());
        return payload(result).get("auctionId").getAsLong();
    }

    private void setEnd(long auctionId, Instant end) throws Exception {
        try (PreparedStatement sql = connection.prepareStatement(
                "UPDATE auctions SET start_at = ?, end_at = ? WHERE auction_id = ?")) {
            sql.setString(1, end.minusSeconds(3600).toString());
            sql.setString(2, end.toString());
            sql.setLong(3, auctionId);
            sql.executeUpdate();
        }
    }

    private String scalarString(String sql) throws Exception {
        try (var query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private long scalarLong(String sql) throws Exception {
        return Long.parseLong(scalarString(sql));
    }

    private static JsonObject payload(ResponseEnvelope result) {
        return JsonParser.parseString(result.payloadJson()).getAsJsonObject();
    }

    private static JsonArray array(ResponseEnvelope result) {
        assertTrue(result.ok(), result.message());
        return JsonParser.parseString(result.payloadJson()).getAsJsonArray();
    }

    private static JsonObject object(Object... pairs) {
        var result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i + 1] instanceof Number number) {
                result.addProperty(pairs[i].toString(), number);
            } else {
                result.addProperty(pairs[i].toString(), pairs[i + 1].toString());
            }
        }
        return result;
    }

    private final class Peer implements AutoCloseable {
        private final Socket socket = new Socket("127.0.0.1", port);
        private final BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        private final PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        private final List<EventEnvelope> buffered = new ArrayList<>();

        Peer() throws Exception {
            socket.setSoTimeout(5000);
        }

        String login(String account, String password) throws Exception {
            ResponseEnvelope result = send("LOGIN", null, object("account", account, "password", password));
            assertTrue(result.ok(), result.message());
            return result.sessionId();
        }

        ResponseEnvelope send(String action, String token, JsonObject payload) throws Exception {
            String requestId = UUID.randomUUID().toString();
            writer.println(JsonCodec.toJson(new RequestEnvelope("v1", requestId, action, token, payload.toString())));
            for (String line; (line = reader.readLine()) != null;) {
                JsonObject value = JsonParser.parseString(line).getAsJsonObject();
                if (value.has("eventType")) {
                    buffered.add(JsonCodec.fromJson(line, EventEnvelope.class));
                    continue;
                }
                ResponseEnvelope response = JsonCodec.fromJson(line, ResponseEnvelope.class);
                assertEquals(requestId, response.requestId());
                return response;
            }
            throw new AssertionError("socket closed before response");
        }

        EventEnvelope readEvent(EventType expected) throws Exception {
            for (int i = 0; i < buffered.size(); i++) {
                if (buffered.get(i).eventType() == expected) {
                    return buffered.remove(i);
                }
            }
            for (String line; (line = reader.readLine()) != null;) {
                EventEnvelope value = JsonCodec.fromJson(line, EventEnvelope.class);
                if (value.eventType() == expected) {
                    return value;
                }
                buffered.add(value);
            }
            throw new AssertionError("socket closed before " + expected);
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
