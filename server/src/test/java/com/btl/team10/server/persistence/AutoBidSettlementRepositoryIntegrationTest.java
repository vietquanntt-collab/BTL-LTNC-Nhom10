package com.btl.team10.server.persistence;

import com.btl.team10.common.dto.ConfigureAutoBidCommand;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.SettleAuctionCommand;
import com.btl.team10.common.dto.SettlementWriteResult;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoBidSettlementRepositoryIntegrationTest {

    @Test
    void autoBidUpsertKeepsOneActiveRulePerBidder() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createFixture(connection);
            JdbcAutoBidRuleRepository rules = new JdbcAutoBidRuleRepository(connection);

            var first = rules.upsert(new ConfigureAutoBidCommand(
                    fixture.auctionId(), fixture.bidderId(), 10_000, 500));
            var replacement = rules.upsert(new ConfigureAutoBidCommand(
                    fixture.auctionId(), fixture.bidderId(), 20_000, 1_000));

            assertEquals(first.ruleId(), replacement.ruleId());
            assertEquals(20_000, replacement.maxAmountCents());
            assertEquals(1_000, replacement.incrementCents());
            assertEquals(1, rules.findActiveByAuction(fixture.auctionId()).size());
            assertTrue(rules.deactivate(fixture.auctionId(), fixture.bidderId()));
            assertFalse(rules.deactivate(fixture.auctionId(), fixture.bidderId()));
            assertTrue(rules.findActiveByAuction(fixture.auctionId()).isEmpty());
        }
    }

    @Test
    void walletUpdatesRequireTheCurrentOptimisticVersion() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createFixture(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE wallets SET balance_cents = ? WHERE user_id = ?")) {
                statement.setLong(1, 5_000);
                statement.setLong(2, fixture.bidderId());
                statement.executeUpdate();
            }

            JdbcWalletRepository wallets = new JdbcWalletRepository(connection);
            var initial = wallets.findByUserId(fixture.bidderId()).orElseThrow();
            assertTrue(wallets.adjustFrozen(fixture.bidderId(), 1_000, initial.version()));
            assertFalse(wallets.adjustFrozen(fixture.bidderId(), 1_000, initial.version()));

            var frozen = wallets.findByUserId(fixture.bidderId()).orElseThrow();
            assertEquals(1_000, frozen.frozenCents());
            assertTrue(wallets.credit(fixture.sellerId(), 2_000,
                    wallets.findByUserId(fixture.sellerId()).orElseThrow().version()));
            assertEquals(2_000, wallets.findByUserId(fixture.sellerId()).orElseThrow().balanceCents());
        }
    }

    @Test
    void settlementIsIdempotentAndMovesAuctionToPaid() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createFixture(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auctions SET status = 'FINISHED', winner_id = ?, current_price_cents = ?"
                            + " WHERE auction_id = ?")) {
                statement.setLong(1, fixture.bidderId());
                statement.setLong(2, 3_000);
                statement.setLong(3, fixture.auctionId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bids(auction_id, bidder_id, amount_cents, bid_time, status, client_request_id)"
                            + " VALUES (?, ?, ?, ?, 'ACTIVE', ?)")) {
                statement.setLong(1, fixture.auctionId());
                statement.setLong(2, fixture.bidderId());
                statement.setLong(3, 3_000);
                statement.setString(4, Instant.now().toString());
                statement.setString(5, "winning-bid-fixture");
                statement.executeUpdate();
            }

            JdbcSettlementRepository settlements = new JdbcSettlementRepository(connection);
            SettleAuctionCommand command = new SettleAuctionCommand(
                    fixture.auctionId(), fixture.sellerId(), fixture.bidderId(), 3_000, "settle-1");
            SettlementWriteResult first = settlements.settle(command);
            SettlementWriteResult retry = settlements.settle(command);

            assertTrue(first.created());
            assertFalse(retry.created());
            assertEquals(first.settlement(), retry.settlement());
            assertEquals(AuctionStatus.PAID,
                    new JdbcAuctionRepository(connection).findById(fixture.auctionId()).orElseThrow().status());
            assertEquals(1, count(connection, "settlements"));
            assertEquals(1, count(connection,
                    "bids WHERE auction_id = " + fixture.auctionId() + " AND status = 'WINNER'"));
        }
    }

    @Test
    void settlementRejectsAmountThatDoesNotMatchFinalAuctionPrice() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createFixture(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auctions SET status = 'FINISHED', winner_id = ?, current_price_cents = ?"
                            + " WHERE auction_id = ?")) {
                statement.setLong(1, fixture.bidderId());
                statement.setLong(2, 3_000);
                statement.setLong(3, fixture.auctionId());
                statement.executeUpdate();
            }

            var settlements = new JdbcSettlementRepository(connection);
            var error = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> settlements.settle(new SettleAuctionCommand(
                            fixture.auctionId(), fixture.sellerId(), fixture.bidderId(), 2_900, "settle-bad")));
            assertTrue(error.getMessage().contains("amount"));
            assertEquals(0, count(connection, "settlements"));
        }
    }

    @Test
    void settlementAcceptsWinnerAlreadyMarkedByExpirationScheduler() throws Exception {
        try (Connection connection = openDatabase()) {
            Fixture fixture = createFixture(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE auctions SET status = 'FINISHED', winner_id = ?, current_price_cents = ?"
                            + " WHERE auction_id = ?")) {
                statement.setLong(1, fixture.bidderId());
                statement.setLong(2, 3_000);
                statement.setLong(3, fixture.auctionId());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO bids(auction_id, bidder_id, amount_cents, bid_time, status, client_request_id)"
                            + " VALUES (?, ?, ?, ?, 'WINNER', ?)")) {
                statement.setLong(1, fixture.auctionId());
                statement.setLong(2, fixture.bidderId());
                statement.setLong(3, 3_000);
                statement.setString(4, Instant.now().toString());
                statement.setString(5, "scheduler-winning-bid-fixture");
                statement.executeUpdate();
            }

            SettlementWriteResult result = new JdbcSettlementRepository(connection).settle(
                    new SettleAuctionCommand(
                            fixture.auctionId(), fixture.sellerId(), fixture.bidderId(), 3_000,
                            "settle-scheduler-winner"));
            assertTrue(result.created());
            assertEquals(1, count(connection, "bids WHERE auction_id = " + fixture.auctionId()
                    + " AND status = 'WINNER'"));
        }
    }

    private static Connection openDatabase() throws Exception {
        Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        DatabaseInitializer.initialize(connection, databaseRoot(), false);
        return connection;
    }

    private static Fixture createFixture(Connection connection) throws Exception {
        JdbcUserRepository users = new JdbcUserRepository(connection);
        long sellerId = users.insert("settlement-seller", "hash-seller", Role.SELLER);
        long bidderId = users.insert("settlement-bidder", "hash-bidder", Role.BIDDER);
        long itemId = new JdbcItemRepository(connection).insert(new CreateItemCommand(
                sellerId, "Settlement fixture item", "Auto-bid fixture", ItemType.CARD, null));
        Instant start = Instant.now().minusSeconds(60);
        long auctionId = new JdbcAuctionRepository(connection).insert(new CreateAuctionCommand(
                itemId, 1_000, 100, start, start.plusSeconds(3_600)));
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE auctions SET status = 'RUNNING' WHERE auction_id = ?")) {
            statement.setLong(1, auctionId);
            statement.executeUpdate();
        }
        return new Fixture(sellerId, bidderId, auctionId);
    }

    private static long count(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static Path databaseRoot() {
        Path current = Path.of("database");
        return Files.isDirectory(current.resolve("migrations"))
                ? current.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }

    private record Fixture(long sellerId, long bidderId, long auctionId) {
    }
}
