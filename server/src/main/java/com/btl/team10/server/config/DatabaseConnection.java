package com.btl.team10.server.config;

import com.btl.team10.server.persistence.DatabaseConnectionFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Compatibility facade for callers that need the application's default database.
 * New code should prefer {@link DatabaseConnectionFactory} and pass an explicit path.
 */
public final class DatabaseConnection {
    private static final String DATABASE_PROPERTY = "auction.db.path";

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        String configuredPath = System.getProperty(DATABASE_PROPERTY, "data/auction.db");
        return DatabaseConnectionFactory.open(Path.of(configuredPath));
    }
}
