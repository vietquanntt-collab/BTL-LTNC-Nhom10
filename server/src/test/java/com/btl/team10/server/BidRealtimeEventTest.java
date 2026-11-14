package com.btl.team10.server;

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
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcBidRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Week 3 - Realtime Auction Event Tests")
class BidRealtimeEventTest {

    @Test
    @DisplayName("1. Đặt giá hợp lệ: người đặt nhận BID_ACCEPTED, người giữ giá trước nhận OUTBID")
    void validHigherBidProducesBidAcceptedAndOutbidEvents() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);
            List<EventEnvelope> emittedEvents = new ArrayList<>();

            BidWriteResult first = bidRepo.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050L, "evt-req-1", 0L));
            emittedEvents.add(buildBidAcceptedEvent(first, "evt-req-1"));

            BidWriteResult second = bidRepo.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderTwoId(), 1_100L, "evt-req-2", 1L));
            emittedEvents.add(buildBidAcceptedEvent(second, "evt-req-2"));
            emittedEvents.add(buildOutbidEvent(second, fixture.bidderOneId()));

            assertEquals(BidStatus.OUTBID, bidRepo.findByRequest(
                    fixture.auctionId(), fixture.bidderOneId(), "evt-req-1").orElseThrow().bid().status());
            assertEquals(BidStatus.ACTIVE, bidRepo.findByRequest(
                    fixture.auctionId(), fixture.bidderTwoId(), "evt-req-2").orElseThrow().bid().status());

            assertEquals(3, emittedEvents.size());
            assertEquals(EventType.BID_ACCEPTED, emittedEvents.get(0).eventType());
            assertEquals(1L, emittedEvents.get(0).sequence());

            assertEquals(EventType.BID_ACCEPTED, emittedEvents.get(1).eventType());
            assertEquals(2L, emittedEvents.get(1).sequence());

            assertEquals(EventType.OUTBID, emittedEvents.get(2).eventType());
            assertEquals(2L, emittedEvents.get(2).sequence());
            JsonObject outbidPayload = JsonParser.parseString(emittedEvents.get(2).payloadJson()).getAsJsonObject();
            assertEquals(fixture.bidderOneId(), outbidPayload.get("outbidUserId").getAsLong());
            assertEquals(1_100L, outbidPayload.get("newHighestAmountCents").getAsLong());
        }
    }

    @Test
    @DisplayName("2. Đặt giá thấp hơn bước giá tối thiểu: phát sự kiện BID_REJECTED cho đúng người gửi")
    void lowBidProducesBidRejectedEventWithoutAdvancingSequence() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);

            BidWriteResult accepted = bidRepo.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050L, "evt-req-1", 0L));
            EventEnvelope acceptedEvent = buildBidAcceptedEvent(accepted, "evt-req-1");

            BidRejectedException rejectedEx = assertThrows(BidRejectedException.class,
                    () -> bidRepo.placeBid(new PlaceBidCommand(
                            fixture.auctionId(), fixture.bidderTwoId(), 1_060L, "evt-req-low", 1L)));

            EventEnvelope rejectedEvent = buildBidRejectedEvent(
                    fixture.auctionId(), fixture.bidderTwoId(), "evt-req-low",
                    accepted.auctionVersion(), rejectedEx);

            assertEquals(EventType.BID_REJECTED, rejectedEvent.eventType());
            assertEquals(acceptedEvent.sequence(), rejectedEvent.sequence(),
                    "Sự kiện BID_REJECTED không được làm tăng sequence của phiên đấu giá");

            JsonObject payload = JsonParser.parseString(rejectedEvent.payloadJson()).getAsJsonObject();
            assertEquals("evt-req-low", payload.get("requestId").getAsString());
            assertEquals(fixture.bidderTwoId(), payload.get("userId").getAsLong());
            assertEquals(ErrorCode.BID_TOO_LOW.name(), payload.get("errorCode").getAsString());
        }
    }

    @Test
    @DisplayName("3. Phiên đấu giá kết thúc: phát AUCTION_FINISHED và từ chối lượt đặt giá đến muộn")
    void finishedAuctionProducesAuctionFinishedAndRejectsLateBids() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bidRepo = new JdbcBidRepository(connection);

            BidWriteResult winningBid = bidRepo.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050L, "evt-req-win", 0L));

            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
                stmt.setString(1, AuctionStatus.FINISHED.name());
                stmt.setLong(2, fixture.auctionId());
                stmt.executeUpdate();
            }

            EventEnvelope finishedEvent = buildAuctionFinishedEvent(
                    fixture.auctionId(), winningBid.auctionVersion() + 1,
                    fixture.bidderOneId(), winningBid.currentPriceCents());

            assertEquals(EventType.AUCTION_FINISHED, finishedEvent.eventType());
            JsonObject finishPayload = JsonParser.parseString(finishedEvent.payloadJson()).getAsJsonObject();
            assertEquals(fixture.bidderOneId(), finishPayload.get("winnerUserId").getAsLong());
            assertEquals(1_050L, finishPayload.get("finalPriceCents").getAsLong());

            BidRejectedException lateEx = assertThrows(BidRejectedException.class,
                    () -> bidRepo.placeBid(new PlaceBidCommand(
                            fixture.auctionId(), fixture.bidderTwoId(), 1_200L, "evt-req-late", 1L)));
            assertEquals(ErrorCode.AUCTION_NOT_OPEN, lateEx.errorCode());

            EventEnvelope lateRejected = buildBidRejectedEvent(
                    fixture.auctionId(), fixture.bidderTwoId(), "evt-req-late",
                    finishedEvent.sequence(), lateEx);
            assertEquals(EventType.BID_REJECTED, lateRejected.eventType());
        }
    }

    @Test
    @DisplayName("4. Truyền tuần tự EventEnvelope qua TCP Socket cho 2 Client cùng theo dõi phiên")
    void multipleSocketClientsReceiveOrderedEventEnvelopes() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0);
             Socket clientOne = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSideOne = serverSocket.accept();
             Socket clientTwo = new Socket("127.0.0.1", serverSocket.getLocalPort());
             Socket serverSideTwo = serverSocket.accept()) {

            clientOne.setSoTimeout(3_000);
            clientTwo.setSoTimeout(3_000);

            PrintWriter outOne = new PrintWriter(serverSideOne.getOutputStream(), true, StandardCharsets.UTF_8);
            PrintWriter outTwo = new PrintWriter(serverSideTwo.getOutputStream(), true, StandardCharsets.UTF_8);
            BufferedReader inOne = new BufferedReader(new InputStreamReader(clientOne.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader inTwo = new BufferedReader(new InputStreamReader(clientTwo.getInputStream(), StandardCharsets.UTF_8));

            EventEnvelope e1 = new EventEnvelope("v1", "evt-1", EventType.BID_ACCEPTED, 100L, 1L, Instant.now(), "{\"amountCents\":1050}");
            EventEnvelope e2 = new EventEnvelope("v1", "evt-2", EventType.OUTBID, 100L, 2L, Instant.now(), "{\"amountCents\":1100}");
            EventEnvelope e3 = new EventEnvelope("v1", "evt-3", EventType.AUCTION_FINISHED, 100L, 3L, Instant.now(), "{\"winnerUserId\":2}");

            for (EventEnvelope event : List.of(e1, e2, e3)) {
                String jsonLine = JsonCodec.toJson(event);
                outOne.println(jsonLine);
                outTwo.println(jsonLine);
            }

            List<EventType> clientOneEvents = List.of(
                    JsonCodec.fromJson(inOne.readLine(), EventEnvelope.class).eventType(),
                    JsonCodec.fromJson(inOne.readLine(), EventEnvelope.class).eventType(),
                    JsonCodec.fromJson(inOne.readLine(), EventEnvelope.class).eventType()
            );
            List<EventType> clientTwoEvents = List.of(
                    JsonCodec.fromJson(inTwo.readLine(), EventEnvelope.class).eventType(),
                    JsonCodec.fromJson(inTwo.readLine(), EventEnvelope.class).eventType(),
                    JsonCodec.fromJson(inTwo.readLine(), EventEnvelope.class).eventType()
            );

            List<EventType> expectedOrder = List.of(
                    EventType.BID_ACCEPTED, EventType.OUTBID, EventType.AUCTION_FINISHED);
            assertEquals(expectedOrder, clientOneEvents);
            assertEquals(expectedOrder, clientTwoEvents);
            assertTrue(clientOne.isConnected() && clientTwo.isConnected());
        }
    }

    private static EventEnvelope buildBidAcceptedEvent(BidWriteResult result, String requestId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("bidId", result.bid().bidId());
        payload.addProperty("userId", result.bid().bidderId());
        payload.addProperty("amountCents", result.currentPriceCents());
        return new EventEnvelope(
                "v1", "evt-" + UUID.randomUUID(), EventType.BID_ACCEPTED,
                result.bid().auctionId(), result.auctionVersion(), Instant.now(), payload.toString());
    }

    private static EventEnvelope buildOutbidEvent(BidWriteResult latestResult, long outbidUserId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("outbidUserId", outbidUserId);
        payload.addProperty("newLeaderUserId", latestResult.bid().bidderId());
        payload.addProperty("newHighestAmountCents", latestResult.currentPriceCents());
        return new EventEnvelope(
                "v1", "evt-" + UUID.randomUUID(), EventType.OUTBID,
                latestResult.bid().auctionId(), latestResult.auctionVersion(), Instant.now(), payload.toString());
    }

    private static EventEnvelope buildBidRejectedEvent(long auctionId, long userId, String requestId,
                                                       long currentSequence, BidRejectedException ex) {
        JsonObject payload = new JsonObject();
        payload.addProperty("requestId", requestId);
        payload.addProperty("userId", userId);
        payload.addProperty("errorCode", ex.errorCode().name());
        payload.addProperty("reason", ex.getMessage());
        return new EventEnvelope(
                "v1", "evt-" + UUID.randomUUID(), EventType.BID_REJECTED,
                auctionId, Math.max(1L, currentSequence), Instant.now(), payload.toString());
    }

    private static EventEnvelope buildAuctionFinishedEvent(long auctionId, long sequence,
                                                           long winnerUserId, long finalPriceCents) {
        JsonObject payload = new JsonObject();
        payload.addProperty("winnerUserId", winnerUserId);
        payload.addProperty("finalPriceCents", finalPriceCents);
        return new EventEnvelope(
                "v1", "evt-" + UUID.randomUUID(), EventType.AUCTION_FINISHED,
                auctionId, sequence, Instant.now(), payload.toString());
    }

    private static Connection openDatabase() throws Exception {
        Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        DatabaseInitializer.initialize(connection, databaseRoot(), false);
        return connection;
    }

    private static Fixture createRunningAuction(Connection connection) throws Exception {
        JdbcUserRepository users = new JdbcUserRepository(connection);
        long sellerId = users.insert("evt-seller", "hash-seller", Role.SELLER);
        long bidderOneId = users.insert("evt-bidder-1", "hash-1", Role.BIDDER);
        long bidderTwoId = users.insert("evt-bidder-2", "hash-2", Role.BIDDER);

        long itemId = new JdbcItemRepository(connection).insert(new CreateItemCommand(
                sellerId, "Realtime Event Item", "Week 3 realtime event fixture", ItemType.CARD, null));
        Instant start = Instant.now().minusSeconds(60);
        long auctionId = new JdbcAuctionRepository(connection).insert(new CreateAuctionCommand(
                itemId, 1_000L, 50L, start, start.plusSeconds(3_600)));
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
            stmt.setString(1, AuctionStatus.RUNNING.name());
            stmt.setLong(2, auctionId);
            stmt.executeUpdate();
        }
        return new Fixture(auctionId, bidderOneId, bidderTwoId);
    }

    private static Path databaseRoot() {
        Path current = Path.of("database");
        return Files.isDirectory(current.resolve("migrations"))
                ? current.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }

    private record Fixture(long auctionId, long bidderOneId, long bidderTwoId) {
    }
}