package com.btl.team10.server.network;

import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Locale;
import java.util.Optional;

/** Handles one line-delimited connection using the shared JSON contract. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final Object databaseLock;
    private final PlaceBidService placeBidService;
    private final EventPublisher eventPublisher;
    private EventPublisher.ClientChannel channel;

    public ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                         ItemRepository itemRepository, AuctionRepository auctionRepository) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository, new Object(), null, null);
    }

    ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                  ItemRepository itemRepository, AuctionRepository auctionRepository,
                  Object databaseLock) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository, databaseLock, null, null);
    }

    public ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                         ItemRepository itemRepository, AuctionRepository auctionRepository,
                         Object databaseLock, PlaceBidService placeBidService, EventPublisher eventPublisher) {
        this.socket = socket;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.databaseLock = Objects.requireNonNull(databaseLock, "databaseLock");
        this.placeBidService = placeBidService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            if (eventPublisher != null) {
                this.channel = eventPublisher.register(eventLine -> {
                    synchronized (writer) {
                        writer.println(eventLine);
                    }
                });
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                boolean quit = isQuit(line.trim());
                String response;
                synchronized (databaseLock) {
                    response = line.trim().startsWith("{")
                            ? handleJson(line.trim()) : handlePipe(line.trim());
                }
                synchronized (writer) {
                    writer.println(response);
                }
                if (quit) {
                    break;
                }
            }
        } catch (Exception error) {
            System.err.println("Client connection failed: " + error.getMessage());
        } finally {
            if(eventPublisher != null && channel != null){
                eventPublisher.unregister(channel);
            }
            try {
                socket.close();
            } catch (Exception ignored) {
                // Socket is already being closed.
            }
        }
    }

    private String handleJson(String line) {
        RequestEnvelope request;
        try {
            request = JsonCodec.fromJson(line, RequestEnvelope.class);
            JsonElement payloadElement = JsonParser.parseString(request.payloadJson());
            if (!payloadElement.isJsonObject()) {
                return failure(request, ErrorCode.INVALID_REQUEST, "payload must be an object");
            }
            JsonObject payload = payloadElement.getAsJsonObject();
            return switch (request.action().toUpperCase(Locale.ROOT)) {
                case "LOGIN" -> login(request, payload);
                case "LOGOUT" -> logout(request);
                case "LIST_AUCTIONS" -> listAuctions(request);
                case "CREATE_ITEM" -> createItem(request, payload);
                case "CREATE_AUCTION" -> createAuction(request, payload);
                case "PLACE_BID" -> placeBid(request, payload);
                case "LIST_BIDS" -> listBids(request, payload);
                default -> failure(request, ErrorCode.INVALID_REQUEST,
                        "Unknown action: " + request.action());
            };
        } catch (RequestFailure error) {
            return failureFor(line, error.code, error.getMessage());
        } catch (JsonParseException | IllegalArgumentException error) {
            return failureFor(line, ErrorCode.INVALID_REQUEST, "Invalid request");
        } catch (SQLException error) {
            return failureFor(line, ErrorCode.INTERNAL_ERROR, "Database error");
        }
    }

    private String login(RequestEnvelope request, JsonObject payload) throws SQLException {
        Optional<SessionManager.UserSession> session = authService.login(
                requiredString(payload, "account"), requiredString(payload, "password"));
        if (session.isEmpty()) {
            return failure(request, ErrorCode.AUTHENTICATION_FAILED, "Invalid account or password");
        }
        SessionManager.UserSession value = session.get();
        if(channel != null){
            channel.bindUser(value.userId());
        }
        JsonObject result = new JsonObject();
        result.addProperty("userId", value.userId());
        result.addProperty("account", value.account());
        result.addProperty("role", value.role());
        return success(request, value.token(), result.toString());
    }

    private String logout(RequestEnvelope request) {
        authService.logout(request.sessionId());
        if (channel != null){
            channel.bindUser(null);
        }
        return success(request, null, "{}");
    }

    private String listAuctions(RequestEnvelope request) throws SQLException {
        requireSession(request, null);
        return success(request, request.sessionId(), JsonCodec.toJson(auctionRepository.findActiveAuctions()));
    }

    private String createItem(RequestEnvelope request, JsonObject payload) throws SQLException {
        SessionManager.UserSession session = requireSession(request, "SELLER");
        CreateItemCommand command = new CreateItemCommand(
                session.userId(), requiredString(payload, "name"), optionalString(payload, "description"),
                ItemType.valueOf(requiredString(payload, "itemType").toUpperCase(Locale.ROOT)),
                optionalString(payload, "imageBase64"));
        JsonObject result = new JsonObject();
        result.addProperty("itemId", itemRepository.insert(command));
        return success(request, request.sessionId(), result.toString());
    }

    private String createAuction(RequestEnvelope request, JsonObject payload) throws SQLException {
        SessionManager.UserSession session = requireSession(request, "SELLER");
        long itemId = requiredLong(payload, "itemId");
        ItemDto item = itemRepository.findById(itemId).orElseThrow(
                () -> new RequestFailure(ErrorCode.NOT_FOUND, "Item does not exist"));
        if (item.sellerId() != session.userId()) {
            return failure(request, ErrorCode.FORBIDDEN, "Seller does not own this item");
        }
        long startPrice = requiredLong(payload, "startPriceCents");
        long minimumIncrement = requiredLong(payload, "minIncrementCents");
        long durationHours = requiredLong(payload, "durationHours");
        if (durationHours < 1 || durationHours > 720) {
            return failure(request, ErrorCode.VALIDATION_ERROR,
                    "durationHours must be between 1 and 720");
        }
        Instant startAt = Instant.now();
        long auctionId = auctionRepository.insert(new CreateAuctionCommand(
                itemId, startPrice, minimumIncrement, startAt, startAt.plusSeconds(durationHours * 3600)));
        JsonObject result = new JsonObject();
        result.addProperty("auctionId", auctionId);
        return success(request, request.sessionId(), result.toString());
    }
    private String placeBid(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (placeBidService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Bidding service unavailable");
        }
        SessionManager.UserSession session = requireSession(request, "BIDDER");
        long auctionId = requiredLong(payload, "auctionId");
        long amountCents = requiredLong(payload, "amountCents");
        ResponseEnvelope response = placeBidService.placeBid(
                request.requestId(), request.sessionId(), session.userId(), auctionId, amountCents);
        return JsonCodec.toJson(response);
    }

    private String listBids(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (placeBidService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Bidding service unavailable");
        }
        requireSession(request, null);
        long auctionId = requiredLong(payload, "auctionId");
        return success(request, request.sessionId(), JsonCodec.toJson(placeBidService.listBidsByAuction(auctionId)));
    }

    private SessionManager.UserSession requireSession(RequestEnvelope request, String role) {
        SessionManager.UserSession session = sessionManager.getSession(request.sessionId()).orElseThrow(
                () -> new RequestFailure(ErrorCode.AUTHENTICATION_FAILED, "Login is required"));
        if (role != null && !role.equalsIgnoreCase(session.role())) {
            throw new RequestFailure(ErrorCode.FORBIDDEN, "Role " + role + " is required");
        }
        return session;
    }

    private String handlePipe(String line) {
        String[] parts = line.split("\\|", -1);
        String action = parts.length == 0 ? "" : parts[0].toUpperCase(Locale.ROOT);
        try {
            return switch (action) {
                case "QUIT", "EXIT" -> "BYE";
                case "LOGIN" -> {
                    if (parts.length != 3) yield "ERROR|LOGIN requires account and password";
                    Optional<SessionManager.UserSession> session = authService.login(parts[1], parts[2]);
                    yield session.map(value -> "SUCCESS|LOGIN|" + value.token() + "|"
                                    + value.account() + "|" + value.role())
                            .orElse("ERROR|Invalid account or password");
                }
                case "CREATE_ITEM" -> {
                    if (parts.length != 5) yield "ERROR|CREATE_ITEM requires token, name, itemType and description";
                    SessionManager.UserSession session = requirePipeSession(parts[1], "SELLER");
                    long itemId = itemRepository.insert(new CreateItemCommand(
                            session.userId(), parts[2], parts[4],
                            ItemType.valueOf(parts[3].toUpperCase(Locale.ROOT)), null));
                    yield "SUCCESS|CREATE_ITEM|" + itemId;
                }
                case "CREATE_AUCTION" -> {
                    if (parts.length != 6) yield "ERROR|CREATE_AUCTION requires token, itemId, startPrice, minIncrement and durationHours";
                    SessionManager.UserSession session = requirePipeSession(parts[1], "SELLER");
                    long itemId = Long.parseLong(parts[2]);
                    ItemDto item = itemRepository.findById(itemId).orElseThrow(
                            () -> new IllegalArgumentException("Item does not exist"));
                    if (item.sellerId() != session.userId()) yield "ERROR|Seller does not own this item";
                    long startPrice = Long.parseLong(parts[3]);
                    long minimumIncrement = Long.parseLong(parts[4]);
                    long durationHours = Long.parseLong(parts[5]);
                    if (durationHours < 1 || durationHours > 720) yield "ERROR|Invalid durationHours";
                    Instant startAt = Instant.now();
                    long auctionId = auctionRepository.insert(new CreateAuctionCommand(
                            itemId, startPrice, minimumIncrement, startAt,
                            startAt.plusSeconds(durationHours * 3600)));
                    yield "SUCCESS|CREATE_AUCTION|" + auctionId;
                }
                case "LIST_AUCTIONS" -> "SUCCESS|LIST_AUCTIONS|COUNT="
                        + auctionRepository.findActiveAuctions().size();
                default -> "ERROR|Unknown action: " + action;
            };
        } catch (RequestFailure error) {
            return "ERROR|" + error.getMessage();
        } catch (SQLException | IllegalArgumentException error) {
            return "ERROR|" + error.getMessage();
        }
    }

    private SessionManager.UserSession requirePipeSession(String token, String role) {
        SessionManager.UserSession session = sessionManager.getSession(token).orElseThrow(
                () -> new RequestFailure(ErrorCode.AUTHENTICATION_FAILED, "Login is required"));
        if (!session.role().equalsIgnoreCase(role)) {
            throw new RequestFailure(ErrorCode.FORBIDDEN, "Role " + role + " is required");
        }
        return session;
    }

    private static String requiredString(JsonObject payload, String key) {
        String value = optionalString(payload, key);
        if (value == null || value.isBlank()) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, key + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalString(JsonObject payload, String key) {
        JsonElement value = payload.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    private static long requiredLong(JsonObject payload, String key) {
        try {
            return payload.get(key).getAsLong();
        } catch (RuntimeException error) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, key + " must be a number");
        }
    }

    private static String success(RequestEnvelope request, String sessionId, String payload) {
        return JsonCodec.toJson(ResponseEnvelope.success(
                request.requestId(), request.action(), sessionId, payload));
    }

    private static String failure(RequestEnvelope request, ErrorCode code, String message) {
        return JsonCodec.toJson(new ResponseEnvelope(
                "v1", request.requestId(), request.action(), false, code, message,
                request.sessionId(), "{}"));
    }

    private static String failureFor(String line, ErrorCode code, String message) {
        try {
            RequestEnvelope request = JsonCodec.fromJson(line, RequestEnvelope.class);
            return failure(request, code, message);
        } catch (RuntimeException ignored) {
            return "{\"protocolVersion\":\"v1\",\"requestId\":\"unknown\","
                    + "\"action\":\"INVALID\",\"ok\":false,\"errorCode\":\""
                    + code + "\",\"message\":\"Invalid request\",\"sessionId\":null,\"payloadJson\":\"{}\"}";
        }
    }

    private static boolean isQuit(String line) {
        return line.equalsIgnoreCase("QUIT") || line.equalsIgnoreCase("EXIT");
    }

    private static final class RequestFailure extends RuntimeException {
        private final ErrorCode code;

        private RequestFailure(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }
    }
}
