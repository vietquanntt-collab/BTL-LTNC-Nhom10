package com.btl.team10.server;

import com.btl.team10.server.config.DatabaseConnection;
import com.btl.team10.server.repository.UserRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.repository.AuctionRepository;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Statement;

public final class ServerApplication {

    private ServerApplication() {
    }

    public static void main(String[] args) {
        System.out.println("Auction server - Week 2 baseline");

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn != null && !conn.isClosed()) {
                System.out.println("-> Ket noi SQLite thanh cong!");

                // Tim thu muc migrations
                File dir = new File("database/migrations");
                if (!dir.exists()) {
                    dir = new File("../database/migrations");
                }

                File[] sqlFiles = dir.listFiles((d, name) -> name.startsWith("V001") && name.endsWith(".sql"));
                if (sqlFiles != null && sqlFiles.length > 0) {
                    File schemaFile = sqlFiles[0];
                    String content = Files.readString(schemaFile.toPath());

                    // Loc bo comment va tach cac lenh SQL hop le
                    String cleanContent = content.replaceAll("(?m)^--.*$", "");
                    String[] queries = cleanContent.split(";");

                    try (Statement stmt = conn.createStatement()) {
                        for (String query : queries) {
                            String trimmed = query.trim();
                            if (!trimmed.isEmpty()) {
                                stmt.execute(trimmed);
                            }
                        }
                    }
                    System.out.println("-> Da tao bang thanh cong tu file: " + schemaFile.getName());
                }

                // Chay thu UserRepository
                UserRepository userRepo = new UserRepository();
                userRepo.createUser("seller1", "hash123", "SELLER");

                var user = userRepo.findByAccount("seller1");
                if (user.isPresent()) {
                    System.out.println("-> Test UserRepository THANH CONG: account=" + user.get().account() + ", role=" + user.get().role());
                } else {
                    System.err.println("-> Test that bai: khong tim thay user!");
                }
                // Test Item & Auction Repository theo schema chuan
                var userOpt = userRepo.findByAccount("seller1");
                if (userOpt.isPresent()) {
                    long sellerId = userOpt.get().userId();

                    ItemRepository itemRepo = new ItemRepository();
                    var itemOpt = itemRepo.createItem(sellerId, "The bai hiem", "The bai co so luong gioi han", "CARD", null);
                    if (itemOpt.isPresent()) {
                        System.out.println("-> Tao item THANH CONG, ID: " + itemOpt.get().itemId());

                        AuctionRepository auctionRepo = new AuctionRepository();
                        var auctionOpt = auctionRepo.createAuction(itemOpt.get().itemId(), 50000L, 5000L, 24);
                        if (auctionOpt.isPresent()) {
                            System.out.println("-> Tao auction THANH CONG, ID: " + auctionOpt.get().auctionId());
                        }
                    }
                }
                // Test Session & Role Authorization
                com.btl.team10.server.session.SessionManager sessionManager = new com.btl.team10.server.session.SessionManager();
                com.btl.team10.server.session.AuthService authService = new com.btl.team10.server.session.AuthService(userRepo, sessionManager);

                var sessionOpt = authService.login("seller1", "hash123");
                if (sessionOpt.isPresent()) {
                    var session = sessionOpt.get();
                    System.out.println("-> Dang nhap THANH CONG! Token: " + session.token());
                    System.out.println("-> Kiem tra quyen SELLER: " + sessionManager.hasRole(session.token(), "SELLER"));
                    System.out.println("-> Kiem tra quyen ADMIN: " + sessionManager.hasRole(session.token(), "ADMIN"));
                } else {
                    System.err.println("-> Dang nhap that bai!");
                }
            }
        } catch (Exception e) {
            System.err.println("-> Loi: " + e.getMessage());
        }
    }
}