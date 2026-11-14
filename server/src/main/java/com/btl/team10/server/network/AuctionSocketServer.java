package com.btl.team10.server.network;

import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thread-per-connection TCP server for the week 2 request contract. */
public final class AuctionSocketServer {
    private final int port;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final PlaceBidService placeBidService;
    private final EventPublisher eventPublisher;
    private final Object databaseLock = new Object();
    private final ExecutorService threadPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "auction-client");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;
    private ServerSocket serverSocket;

    public AuctionSocketServer(int port, AuthService authService, SessionManager sessionManager,
                               ItemRepository itemRepository, AuctionRepository auctionRepository) {
        this(port, authService, sessionManager, itemRepository, auctionRepository, null, new EventPublisher());
    }

    public AuctionSocketServer(int port, AuthService authService, SessionManager sessionManager,
                               ItemRepository itemRepository, AuctionRepository auctionRepository,
                               PlaceBidService placeBidService, EventPublisher eventPublisher) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.placeBidService = placeBidService;
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    }

    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("server is already running");
        }
        running = true;
        try (ServerSocket boundServer = new ServerSocket(port)) {
            serverSocket = boundServer;
            System.out.println("Auction socket server listening on port " + boundServer.getLocalPort());
            while (running) {
                try {
                    Socket client = boundServer.accept();
                    threadPool.submit(new ClientHandler(
                            client, authService, sessionManager, itemRepository, auctionRepository,
                            databaseLock));
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

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // Closing the accept socket is best effort.
        } finally {
            threadPool.shutdownNow();
        }
    }

    /** Returns the configured port, or the actual ephemeral port when configured as zero. */
    public int boundPort() {
        ServerSocket current = serverSocket;
        return current == null ? port : current.getLocalPort();
    }
    public Object databaseLock(){
        return databaseLock;
    }
}
