package com.btl.team10.server.network;

import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuctionSocketServer {

    private final int port;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepo;
    private final AuctionRepository auctionRepo;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public AuctionSocketServer(
            int port,
            AuthService authService,
            SessionManager sessionManager,
            ItemRepository itemRepo,
            AuctionRepository auctionRepo
    ) {
        this.port = port;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepo = itemRepo;
        this.auctionRepo = auctionRepo;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("-> Auction Socket Server dang lang nghe tai cong: " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(new ClientHandler(clientSocket, authService, sessionManager, itemRepo, auctionRepo));
            } catch (IOException e) {
                if (!running) break;
                System.err.println("Loi accept socket: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        threadPool.shutdown();
    }
}