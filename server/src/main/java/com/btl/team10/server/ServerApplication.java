package com.btl.team10.server;

import com.btl.team10.server.network.AuctionSocketServer;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.UserRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

/** Starts the week 2 server with one initialized SQLite database. */
public final class ServerApplication {
    private static final int DEFAULT_PORT = 9999;

    private ServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path databaseRoot = resolveDatabaseRoot();
        Path databaseFile = Path.of(System.getProperty("auction.db.path", "data/auction.db"));
        boolean seed = Boolean.parseBoolean(System.getProperty("auction.db.seed", "true"));
        int port = Integer.parseInt(System.getProperty("auction.server.port", String.valueOf(DEFAULT_PORT)));

        System.out.println("Auction server - week 2");
        System.out.println("Database: " + databaseFile.toAbsolutePath().normalize());
        System.out.println("Schema: " + databaseRoot.toAbsolutePath().normalize());

        try (Connection connection = DatabaseConnectionFactory.open(databaseFile)) {
            DatabaseInitializer.initialize(connection, databaseRoot, seed);

            UserRepository userRepository = new JdbcUserRepository(connection);
            ItemRepository itemRepository = new JdbcItemRepository(connection);
            AuctionRepository auctionRepository = new JdbcAuctionRepository(connection);
            SessionManager sessionManager = new SessionManager();
            AuthService authService = new AuthService(userRepository, sessionManager);
            AuctionSocketServer server = new AuctionSocketServer(
                    port, authService, sessionManager, itemRepository, auctionRepository);

            Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "auction-server-shutdown"));
            server.start();
        }
    }

    static Path resolveDatabaseRoot() {
        String configured = System.getProperty("auction.database.root");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        Path current = Path.of("database");
        if (Files.isDirectory(current.resolve("migrations"))) {
            return current;
        }
        Path parent = Path.of("..", "database");
        if (Files.isDirectory(parent.resolve("migrations"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot find database/migrations from the current directory");
    }
}
