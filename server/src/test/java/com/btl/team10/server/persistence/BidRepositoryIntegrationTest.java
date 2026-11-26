package com.btl.team10.server.persistence;

import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.PlaceBidCommand;
import com.btl.team10.common.exception.BidRejectedException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcBidRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BidRepositoryIntegrationTest {
    @Test
    void acceptedBidsAdvanceVersionAndHistoryInOrder() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bids = new JdbcBidRepository(connection);

            var first = bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050, "request-1", 0));
            var second = bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderTwoId(), 1_100, "request-2", 1));

            assertEquals(1, first.auctionVersion());
            assertEquals(2, second.auctionVersion());
            assertEquals(1_100, second.currentPriceCents());

            var history = bids.findHistory(fixture.auctionId());
            assertEquals(List.of(1L, 2L), history.stream()
                    .map(value -> value.auctionVersion()).toList());
            assertEquals(List.of(1_050L, 1_100L), history.stream()
                    .map(value -> value.amountCents()).toList());
            assertEquals(BidStatus.OUTBID, bids.findByRequest(
                    fixture.auctionId(), fixture.bidderOneId(), "request-1")
                    .orElseThrow().bid().status());
            assertEquals(BidStatus.ACTIVE, bids.findByRequest(
                    fixture.auctionId(), fixture.bidderTwoId(), "request-2")
                    .orElseThrow().bid().status());
        }
    }

    @Test
    void duplicateRequestReturnsOriginalBidWithoutAppendingHistory() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bids = new JdbcBidRepository(connection);
            var command = new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050, "request-1", 0);

            var first = bids.placeBid(command);
            var duplicate = bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_500, "request-1", 0));

            bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderTwoId(), 1_100, "request-2", 1));
            var lateRetry = bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_500, "request-1", 1));

            assertEquals(first.bid().bidId(), duplicate.bid().bidId());
            assertEquals(first.auctionVersion(), duplicate.auctionVersion());
            assertEquals(first.currentPriceCents(), duplicate.currentPriceCents());
            assertEquals(first.bid().bidId(), lateRetry.bid().bidId());
            assertEquals(first.auctionVersion(), lateRetry.auctionVersion());
            assertEquals(first.currentPriceCents(), lateRetry.currentPriceCents());
            assertEquals(2, bids.findHistory(fixture.auctionId()).size());
        }
    }

    @Test
    void lowOrStaleBidIsRejectedWithoutChangingAuction() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            JdbcBidRepository bids = new JdbcBidRepository(connection);
            bids.placeBid(new PlaceBidCommand(
                    fixture.auctionId(), fixture.bidderOneId(), 1_050, "request-1", 0));

            BidRejectedException low = assertThrows(BidRejectedException.class,
                    () -> bids.placeBid(new PlaceBidCommand(
                            fixture.auctionId(), fixture.bidderTwoId(), 1_051, "request-2", 1)));
            assertEquals(ErrorCode.BID_TOO_LOW, low.errorCode());

            BidRejectedException stale = assertThrows(BidRejectedException.class,
                    () -> bids.placeBid(new PlaceBidCommand(
                            fixture.auctionId(), fixture.bidderTwoId(), 1_100, "request-3", 0)));
            assertEquals(ErrorCode.CONFLICT, stale.errorCode());
            assertEquals(1, bids.findHistory(fixture.auctionId()).size());
        }
    }

    @Test
    void closedAuctionRejectsBid() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createRunningAuction(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
                statement.setString(1, AuctionStatus.FINISHED.name());
                statement.setLong(2, fixture.auctionId());
                statement.executeUpdate();
            }

            BidRejectedException error = assertThrows(BidRejectedException.class,
                    () -> new JdbcBidRepository(connection).placeBid(new PlaceBidCommand(
                            fixture.auctionId(), fixture.bidderOneId(), 1_050, "request-1", 0)));
            assertEquals(ErrorCode.AUCTION_NOT_OPEN, error.errorCode());
        }
    }

    private static Connection openDatabase() throws Exception {
        Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        DatabaseInitializer.initialize(connection, databaseRoot(), false);
        return connection;
    }

    private static Fixture createRunningAuction(Connection connection) throws Exception {
        JdbcUserRepository users = new JdbcUserRepository(connection);
        long sellerId = users.insert("week3-seller", "hash-seller", Role.SELLER);
        long bidderOneId = users.insert("week3-bidder-one", "hash-one", Role.BIDDER);
        long bidderTwoId = users.insert("week3-bidder-two", "hash-two", Role.BIDDER);

        long itemId = new JdbcItemRepository(connection).insert(new CreateItemCommand(
                sellerId, "Week 3 item", "Bid repository fixture", ItemType.CARD, null));
        Instant start = Instant.now().minusSeconds(60);
        long auctionId = new JdbcAuctionRepository(connection).insert(new CreateAuctionCommand(
                itemId, 1_000, 50, start, start.plusSeconds(3_600)));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE auctions SET status = ? WHERE auction_id = ?")) {
            statement.setString(1, AuctionStatus.RUNNING.name());
            statement.setLong(2, auctionId);
            statement.executeUpdate();
        }
        return new Fixture(auctionId, bidderOneId, bidderTwoId);
    }

    private static java.nio.file.Path databaseRoot() {
        java.nio.file.Path current = java.nio.file.Path.of("database");
        return java.nio.file.Files.isDirectory(current.resolve("migrations"))
                ? current.toAbsolutePath().normalize()
                : java.nio.file.Path.of("..", "database").toAbsolutePath().normalize();
    }

    private record Fixture(long auctionId, long bidderOneId, long bidderTwoId) {
    }
}
