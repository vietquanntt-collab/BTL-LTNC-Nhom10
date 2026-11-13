package com.btl.team10.server.persistence;

import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migrationsSeedAndCoreRepositoriesWorkTogether() throws Exception {
        Path databaseRoot = databaseRoot();
        try (Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:")) {
            DatabaseInitializer.initialize(connection, databaseRoot, true);
            DatabaseInitializer.initialize(connection, databaseRoot, true);

            assertEquals(3, count(connection, "schema_migrations"));
            assertEquals(4, count(connection, "users"));
            assertEquals(4, count(connection, "wallets"));
            assertEquals(1, count(connection, "items"));
            assertEquals(1, count(connection, "auctions"));
            assertEquals(2, count(connection, "bids"));
            assertEquals(2, count(connection, "bid_history"));
            assertEquals(110_000, scalarLong(connection,
                    "SELECT current_price_cents FROM auctions WHERE auction_id = 1"));
            assertEquals(2, scalarLong(connection,
                    "SELECT version FROM auctions WHERE auction_id = 1"));

            JdbcUserRepository users = new JdbcUserRepository(connection);
            assertTrue(users.findByAccount("SELLER").isPresent(), "account lookup should be case insensitive");

            long sellerId = users.insert("week2-seller", "test-only-hash", Role.SELLER);
            assertEquals(0, users.findById(sellerId).orElseThrow().balanceCents());

            JdbcItemRepository items = new JdbcItemRepository(connection);
            long itemId = items.insert(new CreateItemCommand(
                    sellerId, "Week 2 item", "", ItemType.CARD, null));
            assertEquals("Week 2 item", items.findById(itemId).orElseThrow().name());
            assertEquals(1, items.findBySeller(sellerId).size());

            Instant startAt = Instant.parse("2026-11-07T12:00:00Z");
            JdbcAuctionRepository auctions = new JdbcAuctionRepository(connection);
            long auctionId = auctions.insert(new CreateAuctionCommand(
                    itemId, 1000, 50, startAt, startAt.plusSeconds(3600)));
            assertEquals(AuctionStatus.OPEN, auctions.findById(auctionId).orElseThrow().status());
            assertEquals(1, auctions.findByStatus(AuctionStatus.OPEN).size());

            assertEquals(1, pragmaForeignKeys(connection));
        }
    }

    @Test
    void failedMigrationRollsBackEarlierSchemaChanges() throws Exception {
        Path migrations = Files.createDirectories(temporaryDirectory.resolve("database/migrations"));
        Files.writeString(migrations.resolve("V001__create_sample.sql"),
                "CREATE TABLE sample(id INTEGER PRIMARY KEY);");
        Files.writeString(migrations.resolve("V002__invalid.sql"), "THIS IS NOT SQL;");

        try (Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:")) {
            assertThrows(Exception.class,
                    () -> DatabaseInitializer.initialize(connection, temporaryDirectory.resolve("database"), false));
            assertFalse(tableExists(connection, "sample"));
            assertFalse(tableExists(connection, "schema_migrations"));
            assertTrue(connection.getAutoCommit(), "initializer must restore auto-commit after rollback");
        }
    }

    private static int count(Connection connection, String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static long scalarLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    private static int pragmaForeignKeys(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("PRAGMA foreign_keys");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static Path databaseRoot() {
        Path fromRepository = Path.of("database");
        return Files.isDirectory(fromRepository.resolve("migrations"))
                ? fromRepository.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }
}
