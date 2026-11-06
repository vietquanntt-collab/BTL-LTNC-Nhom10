package com.btl.team10.server.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Applies new SQLite migrations and an optional demo seed atomically. */
public final class DatabaseInitializer {
    private static final String MIGRATION_TABLE = "schema_migrations";

    private DatabaseInitializer() {
    }

    public static void initialize(Connection connection, Path databaseRoot, boolean seed)
            throws IOException, SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(databaseRoot, "databaseRoot");
        Path migrations = databaseRoot.resolve("migrations");
        Path seedFile = databaseRoot.resolve("seed/V001__demo_seed.sql");
        if (!Files.isDirectory(migrations)) {
            throw new IOException("Migration directory does not exist: " + migrations);
        }

        if (!connection.getAutoCommit()) {
            throw new IllegalStateException("Database initialization requires a connection in auto-commit mode");
        }

        connection.setAutoCommit(false);
        try {
            createMigrationTable(connection);
            List<Path> migrationFiles = listMigrationFiles(migrations);
            for (Path migration : migrationFiles) {
                String version = migration.getFileName().toString();
                if (!isApplied(connection, version)) {
                    SqlScriptRunner.executeFile(connection, migration);
                    recordApplied(connection, version);
                }
            }
            if (seed) {
                if (!Files.isRegularFile(seedFile)) {
                    throw new IOException("Demo seed file does not exist: " + seedFile);
                }
                SqlScriptRunner.executeFile(connection, seedFile);
            }
            connection.commit();
        } catch (IOException | SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static List<Path> listMigrationFiles(Path migrations) throws IOException {
        try (var stream = Files.list(migrations)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (files.isEmpty()) {
                throw new IOException("No SQL migration files found in: " + migrations);
            }
            return files;
        }
    }

    private static void createMigrationTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS " + MIGRATION_TABLE + " ("
                    + "version TEXT PRIMARY KEY,"
                    + "installed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))"
                    + ")");
        }
    }

    private static boolean isApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + MIGRATION_TABLE + " WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void recordApplied(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + MIGRATION_TABLE + "(version) VALUES (?)")) {
            statement.setString(1, version);
            statement.executeUpdate();
        }
    }
}
