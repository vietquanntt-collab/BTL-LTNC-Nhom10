package com.btl.team10.client.service;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
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
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Small synchronous TCP client used by JavaFX Tasks, one request per line. */
public final class NetworkClient implements AutoCloseable {
    private static final NetworkClient INSTANCE = new NetworkClient();
    private static final Type AUCTION_LIST_TYPE = new TypeToken<List<AuctionDto>>() { }.getType();

    private final String host = System.getProperty("auction.server.host", "127.0.0.1");
    private final int port = Integer.parseInt(System.getProperty("auction.server.port", "9999"));
    private final int timeoutMillis = Integer.parseInt(System.getProperty("auction.client.timeout", "5000"));
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String sessionId;
    private String role;

    private NetworkClient() {
    }

    public static NetworkClient getInstance() {
        return INSTANCE;
    }

    public synchronized LoginResult login(String account, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("account", account);
        payload.addProperty("password", password);
        ResponseEnvelope response = request("LOGIN", null, payload);
        JsonObject result = JsonParser.parseString(response.payloadJson()).getAsJsonObject();
        sessionId = Objects.requireNonNull(response.sessionId(), "server did not return a session");
        role = result.get("role").getAsString();
        return new LoginResult(sessionId, result.get("userId").getAsLong(),
                result.get("account").getAsString(), role);
    }

    public synchronized List<AuctionDto> listAuctions() throws IOException {
        ResponseEnvelope response = request("LIST_AUCTIONS", requireSession(), new JsonObject());
        return JsonCodec.fromJson(response.payloadJson(), AUCTION_LIST_TYPE);
    }

    public synchronized long createItem(String name, String itemType, String description) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("itemType", itemType);
        payload.addProperty("description", description == null ? "" : description);
        JsonObject result = responseObject(request("CREATE_ITEM", requireSession(), payload));
        return result.get("itemId").getAsLong();
    }

    public synchronized long createAuction(long itemId, long startPriceCents,
                                            long minIncrementCents, long durationHours) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("itemId", itemId);
        payload.addProperty("startPriceCents", startPriceCents);
        payload.addProperty("minIncrementCents", minIncrementCents);
        payload.addProperty("durationHours", durationHours);
        JsonObject result = responseObject(request("CREATE_AUCTION", requireSession(), payload));
        return result.get("auctionId").getAsLong();
    }

    public synchronized String role() {
        return role;
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private ResponseEnvelope request(String action, String requestSessionId, JsonObject payload)
            throws IOException {
        ensureConnected();
        RequestEnvelope request = new RequestEnvelope(
                "v1", UUID.randomUUID().toString(), action, requestSessionId, payload.toString());
        writer.println(JsonCodec.toJson(request));
        String line = reader.readLine();
        if (line == null) {
            close();
            throw new IOException("Server closed the connection");
        }
        ResponseEnvelope response = JsonCodec.fromJson(line, ResponseEnvelope.class);
        if (!response.ok()) {
            throw new IOException(response.errorCode() + ": " + response.message());
        }
        return response;
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

    private void ensureConnected() throws IOException {
        if (isConnected()) {
            return;
        }
        socket = new Socket(host, port);
        socket.setSoTimeout(timeoutMillis);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void close() {
        sessionId = null;
        role = null;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Closing is best effort.
        } finally {
            socket = null;
            reader = null;
            writer = null;
        }
    }

    public record LoginResult(String sessionId, long userId, String account, String role) {
    }
}
