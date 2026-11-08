package com.btl.team10.server.network;

import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepo;
    private final AuctionRepository auctionRepo;

    public ClientHandler(
            Socket socket,
            AuthService authService,
            SessionManager sessionManager,
            ItemRepository itemRepo,
            AuctionRepository auctionRepo
    ) {
        this.socket = socket;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepo = itemRepo;
        this.auctionRepo = auctionRepo;
    }

    @Override
    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("QUIT") || line.equalsIgnoreCase("EXIT")) {
                    writer.println("BYE");
                    break;
                }

                String response = handleCommand(line);
                writer.println(response);
            }
        } catch (Exception e) {
            System.err.println("ClientHandler loi ket noi: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private String handleCommand(String line) {
        String[] parts = line.split("\\|", -1);
        String action = parts[0].toUpperCase();

        return switch (action) {
            case "LOGIN" -> {
                if (parts.length < 3) yield "ERROR|Thieu thong tin LOGIN (dung LOGIN|account|password)";
                var sessionOpt = authService.login(parts[1], parts[2]);
                yield sessionOpt.map(s -> "SUCCESS|LOGIN|" + s.token() + "|" + s.account() + "|" + s.role())
                        .orElse("ERROR|Sai tai khoan hoac mat khau");
            }
            case "CREATE_ITEM" -> {
                if (parts.length < 5) yield "ERROR|Thieu tham so CREATE_ITEM (dung CREATE_ITEM|token|name|itemType|desc)";
                String token = parts[1];
                if (!sessionManager.hasRole(token, "SELLER")) yield "ERROR|Tu choi: Yeu cau quyen SELLER";

                var session = sessionManager.getSession(token).get();
                var itemOpt = itemRepo.createItem(session.userId(), parts[2], parts[4], parts[3], null);
                yield itemOpt.map(item -> "SUCCESS|CREATE_ITEM|" + item.itemId())
                        .orElse("ERROR|Tao item that bai");
            }
            case "CREATE_AUCTION" -> {
                if (parts.length < 6) yield "ERROR|Thieu tham so CREATE_AUCTION (dung CREATE_AUCTION|token|itemId|startPrice|minInc|hours)";
                String token = parts[1];
                if (!sessionManager.hasRole(token, "SELLER")) yield "ERROR|Tu choi: Yeu cau quyen SELLER";

                try {
                    long itemId = Long.parseLong(parts[2]);
                    long startPrice = Long.parseLong(parts[3]);
                    long minInc = Long.parseLong(parts[4]);
                    int hours = Integer.parseInt(parts[5]);

                    var auctionOpt = auctionRepo.createAuction(itemId, startPrice, minInc, hours);
                    yield auctionOpt.map(a -> "SUCCESS|CREATE_AUCTION|" + a.auctionId())
                            .orElse("ERROR|Tao auction that bai");
                } catch (NumberFormatException e) {
                    yield "ERROR|Sai dinh dang so";
                }
            }
            case "LIST_AUCTIONS" -> {
                var active = auctionRepo.findActiveAuctions();
                yield "SUCCESS|LIST_AUCTIONS|COUNT=" + active.size();
            }
            default -> "ERROR|Hanh dong khong hop le: " + action;
        };
    }
}