package com.btl.team10.client.service;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Asynchronous multiplexed TCP client supporting request/response and realtime server push events. */
public final class NetworkClient implements AutoCloseable {
    private static final NetworkClient INSTANCE = new NetworkClient();
    private static final Type AUCTION_LIST_TYPE = new TypeToken<List<AuctionDto>>() { }.getType();
    private static final Type BID_LIST_TYPE = new TypeToken<List<BidDto>>() { }.getType();
    private static final Type ITEM_LIST_TYPE = new TypeToken<List<ItemDto>>() { }.getType();
    private static final Type USER_LIST_TYPE = new TypeToken<List<UserDto>>() { }.getType();

    private final String host = System.getProperty("auction.server.host", "127.0.0.1");
    private final int port = Integer.parseInt(System.getProperty("auction.server.port", "9999"));
    private final int timeoutMillis = Integer.parseInt(System.getProperty("auction.client.timeout", "5000"));

    private final ConcurrentHashMap<String, CompletableFuture<ResponseEnvelope>> pendingRequests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<EventEnvelope>> eventListeners = new CopyOnWriteArrayList<>();
    private final Object writeLock = new Object();

    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile PrintWriter writer;
    private volatile Thread readerThread;
    private volatile boolean manualClose = false;

    private volatile String sessionId;
    private volatile long userId;
    private volatile String account;
    private volatile String role;
    private volatile long balanceCents;
    private volatile long frozenCents;

    private volatile Consumer<Boolean> connectionStateListener;
    private volatile BiConsumer<Long, Long> walletListener;

    private NetworkClient() {
    }

    public static NetworkClient getInstance() {
        return INSTANCE;
    }

    public void addEventListener(Consumer<EventEnvelope> listener) {
        if (listener != null) {
            eventListeners.addIfAbsent(listener);
        }
    }

    public void removeEventListener(Consumer<EventEnvelope> listener) {
        eventListeners.remove(listener);
    }

    /** Callback nhận true khi kết nối lại thành công, false khi mất kết nối bất ngờ. */
    public void setConnectionStateListener(Consumer<Boolean> listener) {
        this.connectionStateListener = listener;
    }

    /** Callback cập nhật (balanceCents, frozenCents) khi số dư ví thay đổi. */
    public void setWalletListener(BiConsumer<Long, Long> listener) {
        this.walletListener = listener;
    }

    public void register(String account, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("account", account);
        payload.addProperty("password", password);
        request("REGISTER", null, payload, UUID.randomUUID().toString());
    }

    public LoginResult login(String account, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("account", account);
        payload.addProperty("password", password);
        ResponseEnvelope response = request("LOGIN", null, payload, UUID.randomUUID().toString());
        JsonObject result = JsonParser.parseString(response.payloadJson()).getAsJsonObject();
        this.sessionId = Objects.requireNonNull(response.sessionId(), "server did not return a session");
        this.userId = result.get("userId").getAsLong();
        this.account = result.get("account").getAsString();
        this.role = result.get("role").getAsString();
        this.balanceCents = result.has("balanceCents") && !result.get("balanceCents").isJsonNull()
                ? result.get("balanceCents").getAsLong() : 0L;
        this.frozenCents = result.has("frozenCents") && !result.get("frozenCents").isJsonNull()
                ? result.get("frozenCents").getAsLong() : 0L;
        notifyWalletChanged();
        return new LoginResult(this.sessionId, this.userId, this.account, this.role, this.balanceCents, this.frozenCents);
    }

    public void logout() {
        String currentSession = this.sessionId;
        if (currentSession != null && !currentSession.isBlank()) {
            try {
                request("LOGOUT", currentSession, new JsonObject(), UUID.randomUUID().toString());
            } catch (Exception ignored) {
            }
        }
        close();
    }

    public List<AuctionDto> listAuctions() throws IOException {
        return listAuctions(null);
    }

    public List<AuctionDto> listAuctions(String statusFilter) throws IOException {
        JsonObject payload = new JsonObject();
        if (statusFilter != null && !statusFilter.isBlank()) {
            payload.addProperty("status", statusFilter);
        }
        ResponseEnvelope response = request("LIST_AUCTIONS", requireSession(), payload, UUID.randomUUID().toString());
        JsonElement parsed = JsonParser.parseString(response.payloadJson());
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("auctions")) {
            return JsonCodec.fromJson(parsed.getAsJsonObject().get("auctions").toString(), AUCTION_LIST_TYPE);
        }
        return JsonCodec.fromJson(response.payloadJson(), AUCTION_LIST_TYPE);
    }

    public AuctionDetailData getAuctionDetail(long auctionId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        ResponseEnvelope response = request("GET_AUCTION", requireSession(), payload, UUID.randomUUID().toString());
        JsonObject root = JsonParser.parseString(response.payloadJson()).getAsJsonObject();

        AuctionDto auction;
        ItemDto item = null;
        List<BidDto> bids = Collections.emptyList();

        if (root.has("auction") && root.get("auction").isJsonObject()) {
            auction = JsonCodec.fromJson(root.get("auction").toString(), AuctionDto.class);
            if (root.has("item") && root.get("item").isJsonObject()) {
                item = JsonCodec.fromJson(root.get("item").toString(), ItemDto.class);
            }
            if (root.has("bids") && root.get("bids").isJsonArray()) {
                bids = JsonCodec.fromJson(root.get("bids").toString(), BID_LIST_TYPE);
            }
        } else {
            auction = JsonCodec.fromJson(response.payloadJson(), AuctionDto.class);
            if (root.has("bids") && root.get("bids").isJsonArray()) {
                bids = JsonCodec.fromJson(root.get("bids").toString(), BID_LIST_TYPE);
            }
        }
        return new AuctionDetailData(auction, item, bids);
    }

    public PlaceBidResult placeBid(long auctionId, long amountCents, String clientRequestId) throws IOException {
        String reqId = (clientRequestId == null || clientRequestId.isBlank())
                ? UUID.randomUUID().toString()
                : clientRequestId;
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("amountCents", amountCents);
        payload.addProperty("clientRequestId", reqId);

        ResponseEnvelope response = request("PLACE_BID", requireSession(), payload, reqId);
        JsonObject result = responseObject(response);
        long bidId = result.has("bidId") ? result.get("bidId").getAsLong() : 0L;
        long currentPrice = result.has("currentPriceCents") ? result.get("currentPriceCents").getAsLong() : amountCents;
        long version = result.has("auctionVersion") ? result.get("auctionVersion").getAsLong() : 0L;
        return new PlaceBidResult(auctionId, bidId, amountCents, currentPrice, version, response.message());
    }

    public AutoBidResult setAutoBid(long auctionId, long maxAmountCents, long incrementCents) throws IOException {
        String reqId = UUID.randomUUID().toString();
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("maxAmountCents", maxAmountCents);
        payload.addProperty("incrementCents", incrementCents);
        payload.addProperty("clientRequestId", reqId);

        ResponseEnvelope response = request("SET_AUTO_BID", requireSession(), payload, reqId);
        JsonObject result = responseObject(response);
        long acceptedMax = result.has("maxAmountCents") ? result.get("maxAmountCents").getAsLong() : maxAmountCents;
        long acceptedInc = result.has("incrementCents") ? result.get("incrementCents").getAsLong() : incrementCents;
        return new AutoBidResult(auctionId, acceptedMax, acceptedInc, response.message());
    }

    public long createItem(String name, String itemType, String description) throws IOException {
        return createItem(name, itemType, description, null);
    }

    public long createItem(String name, String itemType, String description, String imageBase64) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("itemType", itemType);
        payload.addProperty("description", description == null ? "" : description);
        if (imageBase64 != null && !imageBase64.isBlank()) {
            payload.addProperty("imageBase64", imageBase64);
        }
        JsonObject result = responseObject(request("CREATE_ITEM", requireSession(), payload, UUID.randomUUID().toString()));
        return result.get("itemId").getAsLong();
    }

    public void updateItem(long itemId, String name, String itemType, String description) throws IOException {
        updateItem(itemId, name, itemType, description, null);
    }

    public void updateItem(long itemId, String name, String itemType, String description, String imageBase64) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("name", name);
        payload.addProperty("itemType", itemType);
        payload.addProperty("description", description == null ? "" : description);
        if (imageBase64 != null && !imageBase64.isBlank()) {
            payload.addProperty("imageBase64", imageBase64);
        }
        request("UPDATE_ITEM", requireSession(), payload, UUID.randomUUID().toString());
    }

    public void deleteItem(long itemId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        request("DELETE_ITEM", requireSession(), payload, UUID.randomUUID().toString());
    }

    public List<ItemDto> listItems() throws IOException {
        ResponseEnvelope response = request("LIST_ITEMS", requireSession(), new JsonObject(), UUID.randomUUID().toString());
        JsonElement parsed = JsonParser.parseString(response.payloadJson());
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("items")) {
            return JsonCodec.fromJson(parsed.getAsJsonObject().get("items").toString(), ITEM_LIST_TYPE);
        }
        return JsonCodec.fromJson(response.payloadJson(), ITEM_LIST_TYPE);
    }

    public long createAuction(long itemId, long startPriceCents,
                              long minIncrementCents, long durationHours) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("startPriceCents", startPriceCents);
        payload.addProperty("minIncrementCents", minIncrementCents);
        payload.addProperty("durationHours", durationHours);
        JsonObject result = responseObject(request("CREATE_AUCTION", requireSession(), payload, UUID.randomUUID().toString()));
        return result.get("auctionId").getAsLong();
    }

    public void cancelAuction(long auctionId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        request("CANCEL_AUCTION", requireSession(), payload, UUID.randomUUID().toString());
    }

    public List<UserDto> adminListUsers() throws IOException {
        ResponseEnvelope response = request("ADMIN_LIST_USERS", requireSession(), new JsonObject(), UUID.randomUUID().toString());
        JsonElement parsed = JsonParser.parseString(response.payloadJson());
        if (parsed.isJsonObject() && parsed.getAsJsonObject().has("users")) {
            return JsonCodec.fromJson(parsed.getAsJsonObject().get("users").toString(), USER_LIST_TYPE);
        }
        return JsonCodec.fromJson(response.payloadJson(), USER_LIST_TYPE);
    }

    public String role() {
        return role;
    }

    public long userId() {
        return userId;
    }

    public String account() {
        return account;
    }

    public long balanceCents() {
        return balanceCents;
    }

    public long frozenCents() {
        return frozenCents;
    }

    public boolean isLoggedIn() {
        return sessionId != null && !sessionId.isBlank();
    }

    public boolean isConnected() {
        Socket current = this.socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    private ResponseEnvelope request(String action, String requestSessionId, JsonObject payload, String requestId)
            throws IOException {
        ensureConnected();
        CompletableFuture<ResponseEnvelope> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            RequestEnvelope request = new RequestEnvelope(
                    "v1", requestId, action, requestSessionId, payload.toString());
            synchronized (writeLock) {
                if (writer == null) {
                    throw new IOException("Socket writer is not ready");
                }
                writer.println(JsonCodec.toJson(request));
                if (writer.checkError()) {
                    throw new IOException("Failed to write request to server");
                }
            }

            ResponseEnvelope response = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!response.ok()) {
                throw new IOException(response.errorCode() + ": " + response.message());
            }
            return response;
        } catch (TimeoutException e) {
            throw new IOException("Hết thời gian chờ phản hồi từ server (" + action + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Luồng gửi yêu cầu bị gián đoạn", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioEx) {
                throw ioEx;
            }
            throw new IOException("Lỗi kết nối server: " + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    private synchronized void ensureConnected() throws IOException {
        if (isConnected()) {
            return;
        }
        manualClose = false;
        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        startReaderLoop();
    }

    private void startReaderLoop() {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    handleIncomingLine(line.trim());
                }
            } catch (IOException e) {
                if (!manualClose) {
                    failAllPending(new IOException("Mất kết nối tới server: " + e.getMessage(), e));
                }
            } finally {
                cleanupSocketOnly();
                if (!manualClose) {
                    notifyConnectionState(false);
                    attemptAutoReconnect();
                }
            }
        }, "auction-socket-reader");
        thread.setDaemon(true);
        this.readerThread = thread;
        thread.start();
    }

    private void handleIncomingLine(String line) {
        try {
            JsonElement element = JsonParser.parseString(line);
            if (!element.isJsonObject()) {
                return;
            }
            JsonObject obj = element.getAsJsonObject();

            if (obj.has("eventType") && !obj.has("ok")) {
                EventEnvelope event = JsonCodec.fromJson(line, EventEnvelope.class);
                if (event.eventType() == EventType.BALANCE_CHANGED) {
                    handleBalanceEvent(event.payloadJson());
                }
                for (Consumer<EventEnvelope> listener : eventListeners) {
                    try {
                        listener.accept(event);
                    } catch (Exception ignored) {
                    }
                }
                return;
            }

            ResponseEnvelope response = JsonCodec.fromJson(line, ResponseEnvelope.class);
            if (response.requestId() != null) {
                CompletableFuture<ResponseEnvelope> future = pendingRequests.remove(response.requestId());
                if (future != null) {
                    future.complete(response);
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể phân tích gói tin JSON từ server: " + e.getMessage());
        }
    }

    private void handleBalanceEvent(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return;
        }
        try {
            JsonObject payload = JsonParser.parseString(payloadJson).getAsJsonObject();
            if (payload.has("userId") && payload.get("userId").getAsLong() != this.userId) {
                return;
            }
            if (payload.has("balanceCents") && !payload.get("balanceCents").isJsonNull()) {
                this.balanceCents = payload.get("balanceCents").getAsLong();
            }
            if (payload.has("frozenCents") && !payload.get("frozenCents").isJsonNull()) {
                this.frozenCents = payload.get("frozenCents").getAsLong();
            }
            notifyWalletChanged();
        } catch (Exception ignored) {
        }
    }

    private void notifyWalletChanged() {
        BiConsumer<Long, Long> listener = this.walletListener;
        if (listener != null) {
            listener.accept(this.balanceCents, this.frozenCents);
        }
    }

    private void attemptAutoReconnect() {
        Thread reconnectThread = new Thread(() -> {
            int maxRetries = 10;
            for (int attempt = 1; attempt <= maxRetries && !manualClose; attempt++) {
                try {
                    Thread.sleep(Math.min(1000L * attempt, 5000L));
                    ensureConnected();
                    notifyConnectionState(true);
                    return;
                } catch (Exception ignored) {
                }
            }
        }, "auction-socket-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void notifyConnectionState(boolean connected) {
        Consumer<Boolean> listener = this.connectionStateListener;
        if (listener != null) {
            listener.accept(connected);
        }
    }

    private void failAllPending(IOException error) {
        pendingRequests.forEach((id, future) -> future.completeExceptionally(error));
        pendingRequests.clear();
    }

    private JsonObject responseObject(ResponseEnvelope response) {
        return JsonParser.parseString(response.payloadJson()).getAsJsonObject();
    }

    private String requireSession() throws IOException {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IOException("Login is required");
        }
        return sessionId;
    }

    private synchronized void cleanupSocketOnly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
            reader = null;
            writer = null;
        }
    }

    @Override
    public synchronized void close() {
        manualClose = true;
        sessionId = null;
        userId = 0L;
        account = null;
        role = null;
        balanceCents = 0L;
        frozenCents = 0L;
        failAllPending(new IOException("Client closed"));
        cleanupSocketOnly();
    }

    public record LoginResult(String sessionId, long userId, String account, String role,
                              long balanceCents, long frozenCents) {
    }

    public record AuctionDetailData(AuctionDto auction, ItemDto item, List<BidDto> bids) {
    }

    public record PlaceBidResult(long auctionId, long bidId, long amountCents,
                                 long currentPriceCents, long auctionVersion, String message) {
    }

    public record AutoBidResult(long auctionId, long maxAmountCents, long incrementCents, String message) {
    }
}