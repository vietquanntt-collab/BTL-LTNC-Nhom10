package com.btl.n10.Network;

import com.btl.n10.Connection.DataConnection;
import com.btl.n10.DTO.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.btl.n10.Util.LocalDateTimeAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final ConcurrentHashMap<String, ClientHandler> clients;
    private final String token; // UUID khi kết nối TCP - dùng làm key trong clients map

    /**
     * FIX CRITICAL: loginSessionId là sessionId được tạo khi user LOGIN thành công.
     * Đây là key trong ServerSessionManager.sessions.
     * Hai key này (token vs loginSessionId) hoàn toàn khác nhau - trước đây
     * SettlementHandler dùng token để tìm trong ServerSessionManager -> luôn null
     * -> isWinner không bao giờ được set -> không client nào biết mình thắng.
     */
    private volatile String loginSessionId = null;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private final RequestHandler requestHandler;

    public ClientHandler(Socket socket,
                         ConcurrentHashMap<String, ClientHandler> clients,
                         String token) throws IOException {
        this.socket = socket;
        this.clients = clients;
        this.token = token;

        // Khởi tạo luồng đọc/ghi text qua Socket với bộ đệm (autoFlush = true)
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);

        // Khởi tạo bộ điều hướng request gắn liền với phiên làm việc của client này
        this.requestHandler = new RequestHandler(this, clients);
    }

    /**
     * Gửi chuỗi JSON về phía client một cách đồng bộ (chống race condition khi ghi đè output)
     */
    public synchronized void send(String json) {
        out.println(json);
    }

    /** Gọi từ RequestHandler.handleLogin() sau khi tạo session thành công. */
    public void setLoginSessionId(String sessionId) {
        this.loginSessionId = sessionId;
    }

    /**
     * Trả về loginSessionId - dùng trong SettlementHandler để tra cứu
     * user trong ServerSessionManager đúng key.
     */
    public String getLoginSessionId() {
        return loginSessionId;
    }

    @Override
    public void run() {
        try {
            String json;
            // Vòng lặp liên tục lắng nghe các gói tin JSON gửi lên từ Client
            while ((json = in.readLine()) != null) {
                try {
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    String action = obj.get("action").getAsString();
                    switch (action) {
                        case "LOGIN" -> requestHandler.handleLogin(gson.fromJson(json, LoginRequest.class));
                        case "REGISTER" -> requestHandler.handleRegister(gson.fromJson(json, RegisterRequest.class));
                        case "BID" -> requestHandler.handleBid(gson.fromJson(json, BidRequest.class));
                        case "ADD_ITEM" -> requestHandler.handleAddItem(gson.fromJson(json, AddItemRequest.class));
                        case "AUTO_BID" -> requestHandler.handleAutoBid(gson.fromJson(json, AutoBidRequest.class));
                        case "DEPOSIT" -> requestHandler.handleDeposit(gson.fromJson(json, DepositRequest.class));
                        case "UPGRADE_SELLER" -> requestHandler.handleUpgradeSeller(gson.fromJson(json, UpgradeSellerRequest.class));
                        case "GET_USER_BIDS" -> requestHandler.handleGetUserBids(gson.fromJson(json, GetUserBidsRequest.class));
                        case "GET_USER_BALANCE" -> requestHandler.handleGetUserBalance(gson.fromJson(json, GetUserBalanceRequest.class));
                        case "GET_SELLER_ITEMS" -> requestHandler.handleGetSellerItems(gson.fromJson(json, GetSellerItemsRequest.class));
                        case "GET_AUCTION_LIST" -> requestHandler.handleGetAuctionList(gson.fromJson(json, GetAuctionListRequest.class));
                        case "GET_AUCTION_DETAIL" -> requestHandler.handleGetAuctionDetail(gson.fromJson(json, GetAuctionDetailRequest.class));
                        case "ADMIN_GET_DASHBOARD",
                             "ADMIN_GET_USERS",
                             "ADMIN_GET_AUCTIONS",
                             "ADMIN_GET_ITEMS",
                             "ADMIN_UPGRADE_USER",
                             "ADMIN_DEMOTE_USER",
                             "ADMIN_DELETE_USER",
                             "ADMIN_CLOSE_AUCTION",
                             "ADMIN_CANCEL_AUCTION",
                             "ADMIN_DELETE_AUCTION",
                             "ADMIN_DELETE_ITEM" -> requestHandler.handleAdmin(gson.fromJson(json, AdminRequest.class));
                        default -> System.err.println("[ClientHandler] Khong nhan dien duoc action: " + action);
                    }
                } catch (JsonSyntaxException e) {
                    System.err.println("JSON loi: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Client ngat ket noi: " + token);
        } finally {
            // Dọn dẹp tài nguyên khi client thoát hoặc đứt kết nối mạng
            clients.remove(token);
            DataConnection.closeConnection(); // Giải phóng ThreadLocal connection gắn với luồng này
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}