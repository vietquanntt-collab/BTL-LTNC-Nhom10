package com.btl.team10.client.testing;

import com.btl.team10.server.bidding.*;
import com.btl.team10.server.network.*;
import com.btl.team10.server.persistence.*;
import com.btl.team10.server.repository.*;
import com.btl.team10.server.session.*;
import java.nio.file.*;
import java.sql.Connection;

public final class ServerFixture implements AutoCloseable {
    public final Connection database;
    public final AuctionSocketServer server;
    private final Thread thread;
    public ServerFixture() throws Exception {
        database = DatabaseConnectionFactory.open("jdbc:sqlite::memory:");
        Path root = Files.isDirectory(Path.of("database")) ? Path.of("database") : Path.of("../database");
        DatabaseInitializer.initialize(database, root, true);
        var sessions = new SessionManager(); var events = new EventPublisher();
        server = new AuctionSocketServer(0, new AuthService(new JdbcUserRepository(database), sessions), sessions,
                new JdbcItemRepository(database), new JdbcAuctionRepository(database), new PlaceBidService(database, events),
                new SettlementService(database, new JdbcSettlementRepository(database), new JdbcWalletRepository(database), events), events);
        thread = new Thread(() -> { try { server.start(); } catch (Exception error) { throw new RuntimeException(error); } }, "client-test-server");
        thread.setDaemon(true); thread.start();
        for (int i=0; i<200 && server.boundPort()==0; i++) Thread.sleep(10);
        if (server.boundPort()==0) throw new IllegalStateException("Test server failed to bind");
    }
    public int port() { return server.boundPort(); }
    public void close() throws Exception { server.stop(); thread.join(2000); database.close(); }
}
