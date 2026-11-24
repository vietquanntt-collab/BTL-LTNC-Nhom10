package com.btl.team10.server;

import com.btl.team10.server.bidding.AntiSnipePolicy;
import com.btl.team10.server.bidding.AuctionExpirationScheduler;
import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.bidding.SettlementService;
import com.btl.team10.server.network.AuctionSocketServer;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.AutoBidRuleRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcSettlementRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.btl.team10.server.repository.SettlementRepository;
import com.btl.team10.server.repository.UserRepository;
import com.btl.team10.server.repository.WalletRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/** Starts the auction server with one initialized SQLite database. */
public final class ServerApplication {
    private static final int DEFAULT_PORT = 9999;

    private ServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        Path databaseRoot = resolveDatabaseRoot();
        Path databaseFile = Path.of(System.getProperty("auction.db.path", "data/auction.db"));
        boolean seed = Boolean.parseBoolean(System.getProperty("auction.db.seed", "true"));
        int port = Integer.parseInt(System.getProperty("auction.server.port", String.valueOf(DEFAULT_PORT)));
        AntiSnipePolicy antiSnipePolicy = AntiSnipePolicy.defaultPolicy();

        System.out.println("=== Nhóm 10 - Online Auction Server ===");
        System.out.println("[HEALTH] Port: " + port);
        System.out.println("[HEALTH] Database: " + databaseFile.toAbsolutePath().normalize() + " (seed=" + seed + ")");
        System.out.println("[HEALTH] Schema: " + databaseRoot.toAbsolutePath().normalize());
        System.out.println("[HEALTH] Anti-Snipe: window=" + antiSnipePolicy.windowSeconds()
                + "s, extension=" + antiSnipePolicy.extensionSeconds()
                + "s, maxExtensions=" + antiSnipePolicy.maxExtensions());

        Connection connection = DatabaseConnectionFactory.open(databaseFile);
        Object databaseLock = new Object();
        AtomicBoolean closed = new AtomicBoolean(false);
        try {
            DatabaseInitializer.initialize(connection, databaseRoot, seed);

            UserRepository userRepository = new JdbcUserRepository(connection);
            ItemRepository itemRepository = new JdbcItemRepository(connection);
            AuctionRepository auctionRepository = new JdbcAuctionRepository(connection);
            AutoBidRuleRepository autoBidRuleRepository = new JdbcAutoBidRuleRepository(connection);
            WalletRepository walletRepository = new JdbcWalletRepository(connection);
            SettlementRepository settlementRepository = new JdbcSettlementRepository(connection);

            SessionManager sessionManager = new SessionManager();
            AuthService authService = new AuthService(userRepository, sessionManager);
            EventPublisher eventPublisher = new EventPublisher();

            PlaceBidService placeBidService = new PlaceBidService(
                    connection,
                    eventPublisher,
                    autoBidRuleRepository,
                    walletRepository,
                    antiSnipePolicy);
            SettlementService settlementService = new SettlementService(
                    connection,
                    settlementRepository,
                    walletRepository,
                    eventPublisher);

            AuctionSocketServer server = new AuctionSocketServer(
                    port,
                    authService,
                    sessionManager,
                    itemRepository,
                    auctionRepository,
                    placeBidService,
                    settlementService,
                    eventPublisher);
            databaseLock = server.databaseLock();
            AuctionExpirationScheduler scheduler = new AuctionExpirationScheduler(
                    connection,
                    eventPublisher,
                    databaseLock,
                    settlementService);

            final Object lockRef = databaseLock;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[HEALTH] Shutdown signal received, stopping auction server...");
                scheduler.stop();
                server.stop();
                closeConnectionSafely(connection, lockRef, closed);
                System.out.println("[HEALTH] Auction server stopped cleanly.");
            }, "auction-server-shutdown"));

            System.out.println("[HEALTH] Open auctions at startup: " + auctionRepository.findActiveAuctions().size());
            scheduler.start();
            server.start();
        } finally {
            closeConnectionSafely(connection, databaseLock, closed);
        }
    }

    private static void closeConnectionSafely(Connection connection, Object databaseLock, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        synchronized (databaseLock) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (Exception ignored) {
                // Best-effort close on exit.
            }
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
