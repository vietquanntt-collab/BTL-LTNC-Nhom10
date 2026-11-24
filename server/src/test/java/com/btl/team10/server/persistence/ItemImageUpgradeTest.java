package com.btl.team10.server.persistence;

import com.btl.team10.server.repository.JdbcItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemImageUpgradeTest {
    @TempDir Path temporary;
    @Test void upgradesWeek4DatabaseWithoutLosingExistingRows() throws Exception {
        Path root = Files.isDirectory(Path.of("database")) ? Path.of("database") : Path.of("../database");
        Path legacy = temporary.resolve("legacy"); Files.createDirectories(legacy.resolve("migrations"));
        try (var files = Files.list(root.resolve("migrations"))) {
            for (Path file : files.toList()) {
                if (file.getFileName().toString().startsWith("V005")) continue;
                Files.writeString(legacy.resolve("migrations").resolve(file.getFileName()),
                        Files.readString(file).replace("    image_base64     TEXT,", ""));
            }
        }
        try (var connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:")) {
            DatabaseInitializer.initialize(connection, legacy, false);
            try (var sql = connection.createStatement()) {
                sql.execute("INSERT INTO users(user_id,account,password_hash,role) VALUES(50,'legacy-seller','test','SELLER')");
                sql.execute("INSERT INTO items(item_id,seller_id,name,item_type) VALUES(50,50,'Existing item','CARD')");
            }
            DatabaseInitializer.initialize(connection, root, false);
            DatabaseInitializer.initialize(connection, root, false);
            var item = new JdbcItemRepository(connection).findById(50).orElseThrow();
            assertEquals("Existing item", item.name()); assertNull(item.imageBase64());
            try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
                assertTrue(rows.next()); assertEquals(5, rows.getInt(1));
            }
        }
    }
}
