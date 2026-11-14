package com.btl.team10.client.service;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.protocol.EventEnvelope;
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
import java.util.function.Consumer;

/** Asynchronous multiplexed TCP client supporting request/response and realtime server push events. */
public final class NetworkClient implements AutoCloseable {
    private static final NetworkClient INSTANCE = new NetworkClient();
    private static final Type AUCTION_LIST_TYPE = new TypeToken<List<AuctionDto>>() { }.getType();
    private static final Type BID_LIST_TYPE = new TypeToken<List<BidDto>>() { }.getType();

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
    private volatile Consumer<Boolean> connectionStateListener;

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
        return new LoginResult(this.sessionId, this.userId, this.account, this.role);
    }

    public List<AuctionDto> listAuctions() throws IOException {
        ResponseEnvelope response = request("LIST_AUCTIONS", requireSession(), new JsonObject(), UUID.randomUUID().toString());
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
            // Tương thích trường hợp server trả trực tiếp các trường của AuctionDto ở root
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

    public long createItem(String name, String itemType, String description) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("itemType", itemType);
        payload.addProperty("description", description == null ? "" : description);
        JsonObject result = responseObject(request("CREATE_ITEM", requireSession(), payload, UUID.randomUUID().toString()));
        return result.get("itemId").getAsLong();
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

    public String role() {
        return role;
    }

    public long userId() {
        return userId;
    }

    public String account() {
        return account;
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

            // Phân biệt EventEnvelope (server push) và ResponseEnvelope (phản hồi request)
            if (obj.has("eventType") && !obj.has("ok")) {
                EventEnvelope event = JsonCodec.fromJson(line, EventEnvelope.class);
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
        role = null;
        failAllPending(new IOException("Client closed"));
        cleanupSocketOnly();
    }

    public record LoginResult(String sessionId, long userId, String account, String role) {
    }

    public record AuctionDetailData(AuctionDto auction, ItemDto item, List<BidDto> bids) {
    }

    public record PlaceBidResult(long auctionId, long bidId, long amountCents,
                                 long currentPriceCents, long auctionVersion, String message) {
    }
}