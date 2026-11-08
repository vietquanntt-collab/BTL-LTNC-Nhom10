package com.btl.team10.server;

import java.sql.Connection;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcAuctionRepository;

import com.btl.team10.server.config.DatabaseConnection;
import com.btl.team10.server.network.AuctionSocketServer;
import com.btl.team10.server.repository.*;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;

public final class ServerApplication {

    private ServerApplication() {
    }

    public static void main(String[] args) throws Exception{
        System.out.println("Auction server - Week 2 baseline");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("-> Ket noi SQLite thanh cong!");

                File dir = new File("database/migrations");
                if (!dir.exists()) {
                    dir = new File("../database/migrations");
                }

                if (dir.exists()) {
                    File[] files = dir.listFiles((d, name) -> name.endsWith(".sql"));
                    if (files != null) {
                        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
                        for (File f : files) {
                            String sql = java.nio.file.Files.readString(f.toPath());
                            try (Statement stmt = conn.createStatement()) {
                                stmt.executeUpdate(sql);
                                System.out.println("-> Da tao bang thanh cong tu file: " + f.getName());
                            }
                        }
                    }
                }
                Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite:auction.db");
                UserRepository userRepo = new JdbcUserRepository(connection);
                userRepo.createUser("seller1", "hash123", "SELLER");

                ItemRepository itemRepo = new JdbcItemRepository(connection);
                AuctionRepository auctionRepo = new JdbcAuctionRepository(connection);
                SessionManager sessionManager = new SessionManager();
                AuthService authService = new AuthService(userRepo, sessionManager);

                int port = 9999;
                AuctionSocketServer server = new AuctionSocketServer(port, authService, sessionManager, itemRepo, auctionRepo);

                // Chay server tren mot thread rieng
                Thread serverThread = new Thread(() -> {
                    try {
                        server.start();
                    } catch (Exception e) {
                        System.err.println("Loi server: " + e.getMessage());
                    }
                });
                serverThread.setDaemon(true);
                serverThread.start();

                // Doi 200ms de server san sang
                Thread.sleep(200);

                // Gia lap 1 client ket noi vao kiem tra socket baseline
                try (
                        Socket client = new Socket("localhost", port);
                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream()));
                        java.io.PrintWriter out = new java.io.PrintWriter(client.getOutputStream(), true)
                ) {
                    // Test 1: Dang nhap
                    out.println("LOGIN|seller1|hash123");
                    String loginResp = in.readLine();
                    System.out.println("-> Client nhan tu Socket (LOGIN): " + loginResp);

                    String token = loginResp.split("\\|")[2];

                    // Test 2: Tao Item qua socket
                    out.println("CREATE_ITEM|" + token + "|Vat pham hiem|CARD|Mo ta vat pham");
                    System.out.println("-> Client nhan tu Socket (CREATE_ITEM): " + in.readLine());

                    // Test 3: Lay danh sach Auction
                    out.println("LIST_AUCTIONS");
                    System.out.println("-> Client nhan tu Socket (LIST_AUCTIONS): " + in.readLine());

                    out.println("QUIT");
                }

                System.out.println("-> TEST SOCKET BASELINE TUAN 2 HOAN TAT 100%!");
            }
        } catch (Exception e) {
            System.err.println("-> Loi: " + e.getMessage());
        }
    }
}