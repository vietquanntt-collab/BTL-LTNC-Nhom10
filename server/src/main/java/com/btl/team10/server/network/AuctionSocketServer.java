package com.btl.team10.server.network;

import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.bidding.SettlementService;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread-per-connection TCP server for the shared request and realtime event contract. */
public final class AuctionSocketServer {
    private final int port;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final PlaceBidService placeBidService;
    private final SettlementService settlementService;
    private final EventPublisher eventPublisher;
    private final Object databaseLock = new Object();
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final ExecutorService threadPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "auction-client");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public AuctionSocketServer(int port, AuthService authService, SessionManager sessionManager,
                               ItemRepository itemRepository, AuctionRepository auctionRepository) {
        this(port, authService, sessionManager, itemRepository, auctionRepository,
                null, null, new EventPublisher());
    }

    public AuctionSocketServer(int port, AuthService authService, SessionManager sessionManager,
                               ItemRepository itemRepository, AuctionRepository auctionRepository,
                               PlaceBidService placeBidService, EventPublisher eventPublisher) {
        this(port, authService, sessionManager, itemRepository, auctionRepository,
                placeBidService, null, eventPublisher);
    }

    public AuctionSocketServer(int port, AuthService authService, SessionManager sessionManager,
                               ItemRepository itemRepository, AuctionRepository auctionRepository,
                               PlaceBidService placeBidService, SettlementService settlementService,
                               EventPublisher eventPublisher) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.placeBidService = placeBidService;
        this.settlementService = settlementService;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("server is already running");
        }
        running = true;
        try (ServerSocket boundServer = new ServerSocket(port)) {
            serverSocket = boundServer;
            System.out.println("[HEALTH] Auction socket server listening on port " + boundServer.getLocalPort());
            while (running) {
                try {
                    Socket client = boundServer.accept();
                    client.setTcpNoDelay(true);
                    activeSockets.add(client);
                    activeConnections.incrementAndGet();
                    threadPool.submit(() -> {
                        try {
                            new ClientHandler(
                                    client, authService, sessionManager, itemRepository, auctionRepository,
                                    databaseLock, placeBidService, settlementService, eventPublisher,
                                    this::activeConnections).run();
                        } finally {
                            activeSockets.remove(client);
                            activeConnections.decrementAndGet();
                        }
                    });
                } catch (IOException error) {
                    if (running) {
                        throw error;
                    }
                }
            }
        } finally {
            serverSocket = null;
            running = false;
        }
    }

    /** Gracefully stops accepting new clients, waits briefly for in-flight requests, and closes open sockets. */
    public void stop() {
        running = false;
        try {
            ServerSocket current = serverSocket;
            if (current != null && !current.isClosed()) {
                current.close();
            }
        } catch (IOException ignored) {
            // Closing the accept socket is best effort.
        }
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                for (Socket socket : activeSockets) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // Best-effort close during shutdown.
                    }
                }
                threadPool.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            threadPool.shutdownNow();
        } finally {
            activeSockets.clear();
        }
    }

    /** Returns the configured port, or the actual ephemeral port when configured as zero. */
    public int boundPort() {
        ServerSocket current = serverSocket;
        return current == null ? port : current.getLocalPort();
    }

    public int activeConnections() {
        return Math.max(0, activeConnections.get());
    }

    public boolean isRunning() {
        return running;
    }

    public Object databaseLock() {
        return databaseLock;
    }
}