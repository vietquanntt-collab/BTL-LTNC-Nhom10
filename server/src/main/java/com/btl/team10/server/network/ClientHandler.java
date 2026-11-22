package com.btl.team10.server.network;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.bidding.PlaceBidService;
import com.btl.team10.server.bidding.SettlementService;
import com.btl.team10.server.repository.AuctionRepository;
import com.btl.team10.server.repository.ItemRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;

/** Handles one line-delimited connection using the shared JSON contract. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final AuthService authService;
    private final SessionManager sessionManager;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final Object databaseLock;
    private final PlaceBidService placeBidService;
    private final SettlementService settlementService;
    private final EventPublisher eventPublisher;
    private final IntSupplier activeConnectionsSupplier;
    private SocketClientObserver clientObserver;

    public ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                         ItemRepository itemRepository, AuctionRepository auctionRepository) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository,
                new Object(), null, null, null, () -> 1);
    }

    ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                  ItemRepository itemRepository, AuctionRepository auctionRepository,
                  Object databaseLock) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository,
                databaseLock, null, null, null, () -> 1);
    }

    public ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                         ItemRepository itemRepository, AuctionRepository auctionRepository,
                         Object databaseLock, PlaceBidService placeBidService, EventPublisher eventPublisher) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository,
                databaseLock, placeBidService, null, eventPublisher, () -> 1);
    }

    public ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                         ItemRepository itemRepository, AuctionRepository auctionRepository,
                         Object databaseLock, PlaceBidService placeBidService,
                         SettlementService settlementService, EventPublisher eventPublisher) {
        this(socket, authService, sessionManager, itemRepository, auctionRepository,
                databaseLock, placeBidService, settlementService, eventPublisher, () -> 1);
    }

    ClientHandler(Socket socket, AuthService authService, SessionManager sessionManager,
                  ItemRepository itemRepository, AuctionRepository auctionRepository,
                  Object databaseLock, PlaceBidService placeBidService,
                  SettlementService settlementService, EventPublisher eventPublisher,
                  IntSupplier activeConnectionsSupplier) {
        this.socket = socket;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.databaseLock = Objects.requireNonNull(databaseLock, "databaseLock");
        this.placeBidService = placeBidService;
        this.settlementService = settlementService;
        this.eventPublisher = eventPublisher;
        this.activeConnectionsSupplier = activeConnectionsSupplier == null ? () -> 1 : activeConnectionsSupplier;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            if (eventPublisher != null) {
                this.clientObserver = new SocketClientObserver(writer);
                eventPublisher.subscribe(clientObserver);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String trimmed = line.trim();
                boolean quit = isQuit(trimmed);
                String response;
                if (clientObserver != null) {
                    clientObserver.beginRequest();
                }
                try {
                    synchronized (databaseLock) {
                        response = trimmed.startsWith("{")
                                ? handleJson(trimmed) : handlePipe(trimmed);
                    }
                } catch (RuntimeException error) {
                    response = trimmed.startsWith("{")
                            ? failureFor(trimmed, ErrorCode.INTERNAL_ERROR, "Request failed")
                            : "ERROR|Request failed";
                }
                if (clientObserver != null) {
                    clientObserver.finishRequestAndFlush(response);
                } else {
                    synchronized (writer) {
                        writer.println(response);
                    }
                }
                if (quit) {
                    break;
                }
            }
        } catch (SocketException | EOFException disconnected) {
            // Normal client disconnect or socket close during shutdown/reconnect.
        } catch (Exception error) {
            System.err.println("[WARN] Client connection closed with error: " + error.getMessage());
        } finally {
            if (eventPublisher != null && clientObserver != null) {
                clientObserver.bindUser(null);
                eventPublisher.unsubscribe(clientObserver);
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
            if (request == null || request.action() == null || request.action().isBlank()) {
                return failureFor(line, ErrorCode.INVALID_REQUEST, "action must not be blank");
            }
            if (!"v1".equals(request.protocolVersion())) {
                return failure(request, ErrorCode.INVALID_REQUEST, "Unsupported protocol version");
            }
            String rawPayload = request.payloadJson();
            JsonObject payload;
            if (rawPayload == null || rawPayload.isBlank()) {
                payload = new JsonObject();
            } else {
                JsonElement payloadElement = JsonParser.parseString(rawPayload);
                if (!payloadElement.isJsonObject()) {
                    return failure(request, ErrorCode.INVALID_REQUEST, "payload must be an object");
                }
                payload = payloadElement.getAsJsonObject();
            }
            return switch (request.action().toUpperCase(Locale.ROOT)) {
                case "PING", "HEALTH" -> health(request);
                case "REGISTER" -> register(request, payload);
                case "LOGIN" -> login(request, payload);
                case "LOGOUT" -> logout(request);
                case "LIST_AUCTIONS" -> listAuctions(request, payload);
                case "GET_AUCTION" -> getAuction(request, payload);
                case "CREATE_ITEM" -> createItem(request, payload);
                case "LIST_ITEMS" -> listItems(request);
                case "UPDATE_ITEM" -> updateItem(request, payload);
                case "DELETE_ITEM" -> deleteItem(request, payload);
                case "CREATE_AUCTION" -> createAuction(request, payload);
                case "CANCEL_AUCTION" -> cancelAuction(request, payload);
                case "ADMIN_LIST_USERS" -> adminListUsers(request, payload);
                case "PLACE_BID" -> placeBid(request, payload);
                case "LIST_BIDS" -> listBids(request, payload);
                case "SET_AUTO_BID" -> setAutoBid(request, payload);
                case "CANCEL_AUTO_BID" -> cancelAutoBid(request, payload);
                case "SETTLE_AUCTION" -> settleAuction(request, payload);
                default -> failure(request, ErrorCode.INVALID_REQUEST,
                        "Unknown action: " + request.action());
            };
        } catch (RequestFailure error) {
            return failureFor(line, error.code, error.getMessage());
        } catch (ValidationException error) {
            return failureFor(line, error.errorCode(), error.getMessage());
        } catch (SQLException error) {
            return failureFor(line, error.getErrorCode() == 19 ? ErrorCode.CONFLICT : ErrorCode.INTERNAL_ERROR,
                    error.getErrorCode() == 19 ? "Request conflicts with existing data" : "Database error");
        } catch (RuntimeException error) {
            return failureFor(line, ErrorCode.INVALID_REQUEST, "Invalid request");
        }
    }

    private String health(RequestEnvelope request) throws SQLException {
        JsonObject result = new JsonObject();
        result.addProperty("status", "UP");
        result.addProperty("serverTime", Instant.now().toString());
        result.addProperty("activeConnections", activeConnectionsSupplier.getAsInt());
        result.addProperty("openAuctions", auctionRepository.findActiveAuctions().size());
        return success(request, request.sessionId(), result.toString());
    }

    private String register(RequestEnvelope request, JsonObject payload) throws SQLException {
        try {
            long userId = authService.registerBidder(
                    requiredString(payload, "account"), requiredPassword(payload));
            JsonObject result = new JsonObject();
            result.addProperty("userId", userId);
            result.addProperty("role", "BIDDER");
            return success(request, null, result.toString());
        } catch (IllegalArgumentException error) {
            throw new RequestFailure(error.getMessage() != null && error.getMessage().contains("already registered")
                    ? ErrorCode.CONFLICT : ErrorCode.VALIDATION_ERROR, error.getMessage());
        }
    }

    private String login(RequestEnvelope request, JsonObject payload) throws SQLException {
        Optional<SessionManager.UserSession> session = authService.login(
                requiredString(payload, "account"), requiredPassword(payload));
        if (session.isEmpty()) {
            return failure(request, ErrorCode.AUTHENTICATION_FAILED, "Invalid account or password");
        }
        SessionManager.UserSession value = session.get();
        if (clientObserver != null) {
            clientObserver.bindUser(value.userId());
        }
        JsonObject result = new JsonObject();
        result.addProperty("userId", value.userId());
        result.addProperty("account", value.account());
        result.addProperty("role", value.role());
        var user = authService.findUser(value.userId()).orElseThrow(
                () -> new RequestFailure(ErrorCode.NOT_FOUND, "User does not exist"));
        result.addProperty("balanceCents", user.balanceCents());
        result.addProperty("frozenCents", user.frozenCents());
        return success(request, value.token(), result.toString());
    }

    private String logout(RequestEnvelope request) {
        requireSession(request, null);
        authService.logout(request.sessionId());
        if (clientObserver != null) {
            clientObserver.bindUser(null);
        }
        return success(request, null, "{}");
    }

    private String listAuctions(RequestEnvelope request, JsonObject payload) throws SQLException {
        requireSession(request, null);
        String status = optionalString(payload, "status");
        List<AuctionDto> auctions;
        if (status == null || status.isBlank()) {
            auctions = auctionRepository.findActiveAuctions();
        } else if ("ALL".equalsIgnoreCase(status.trim())) {
            auctions = auctionRepository.findAllAuctions();
        } else {
            try {
                auctions = auctionRepository.findByStatus(
                        AuctionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException error) {
                throw new RequestFailure(ErrorCode.VALIDATION_ERROR, "Unknown auction status");
            }
        }
        auctions = auctions.stream().sorted(
                Comparator.comparing(AuctionDto::endAt).thenComparingLong(AuctionDto::auctionId)).toList();
        return success(request, request.sessionId(), JsonCodec.toJson(paginate(auctions, payload)));
    }

    private String getAuction(RequestEnvelope request, JsonObject payload) throws SQLException {
        requireSession(request, null);
        long auctionId = requiredLong(payload, "auctionId");
        var auction = auctionRepository.findById(auctionId).orElseThrow(
                () -> new RequestFailure(ErrorCode.NOT_FOUND, "Auction does not exist"));
        var item = itemRepository.findById(auction.itemId()).orElseThrow(
                () -> new RequestFailure(ErrorCode.NOT_FOUND, "Auction item does not exist"));
        JsonObject result = new JsonObject();
        result.add("auction", JsonParser.parseString(JsonCodec.toJson(auction)));
        result.add("item", JsonParser.parseString(JsonCodec.toJson(item)));
        if (placeBidService != null) {
            result.add("bids", JsonParser.parseString(JsonCodec.toJson(
                    placeBidService.listBidsByAuction(auctionId))));
        } else {
            result.add("bids", new com.google.gson.JsonArray());
        }
        return success(request, request.sessionId(), result.toString());
    }

    private String createItem(RequestEnvelope request, JsonObject payload) throws SQLException {
        SessionManager.UserSession session = requireSession(request, "SELLER");
        CreateItemCommand command = new CreateItemCommand(
                session.userId(), requiredString(payload, "name"), optionalString(payload, "description"),
                itemType(requiredString(payload, "itemType")),
                optionalString(payload, "imageBase64"));
        JsonObject result = new JsonObject();
        result.addProperty("itemId", itemRepository.insert(command));
        return success(request, request.sessionId(), result.toString());
    }

    private String listItems(RequestEnvelope request) throws SQLException {
        var session = requireSession(request, "SELLER");
        return success(request, request.sessionId(), JsonCodec.toJson(itemRepository.findBySeller(session.userId())));
    }

    private ItemDto requireOwnedItem(RequestEnvelope request, JsonObject payload) throws SQLException {
        var session = requireSession(request, null);
        if (!"SELLER".equals(session.role()) && !"ADMIN".equals(session.role())) {
            throw new RequestFailure(ErrorCode.FORBIDDEN, "Seller or Admin role is required");
        }
        var item = itemRepository.findById(positiveLong(payload, "itemId")).orElseThrow(
                () -> new RequestFailure(ErrorCode.NOT_FOUND, "Item does not exist"));
        if (!"ADMIN".equals(session.role()) && item.sellerId() != session.userId()) {
            throw new RequestFailure(ErrorCode.FORBIDDEN, "Seller does not own this item");
        }
        return item;
    }

    private String updateItem(RequestEnvelope request, JsonObject payload) throws SQLException {
        var item = requireOwnedItem(request, payload);
        String name = payload.has("name") ? requiredString(payload, "name") : item.name();
        String description = payload.has("description") ? optionalString(payload, "description") : item.description();
        ItemType type = payload.has("itemType")
                ? itemType(requiredString(payload, "itemType")) : item.itemType();
        if (!itemRepository.update(item.itemId(), item.sellerId(), name, description, type)) {
            throw new RequestFailure(ErrorCode.CONFLICT, "Items linked to an auction cannot be edited");
        }
        return success(request, request.sessionId(), "{}");
    }

    private String deleteItem(RequestEnvelope request, JsonObject payload) throws SQLException {
        var item = requireOwnedItem(request, payload);
        if (!itemRepository.delete(item.itemId(), item.sellerId())) {
            throw new RequestFailure(ErrorCode.CONFLICT, "Item could not be deleted");
        }
        return success(request, request.sessionId(), "{}");
    }

    private String createAuction(RequestEnvelope request, JsonObject payload) throws SQLException {
        SessionManager.UserSession session = requireSession(request, "SELLER");
        long itemId = positiveLong(payload, "itemId");
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

    private String cancelAuction(RequestEnvelope request, JsonObject payload) throws SQLException {
        var session = requireSession(request, null);
        if (settlementService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Cancellation service unavailable");
        }
        return JsonCodec.toJson(settlementService.cancelAuctionRequest(
                request.requestId(), request.sessionId(), session.userId(), session.role(),
                positiveLong(payload, "auctionId")));
    }

    private String adminListUsers(RequestEnvelope request, JsonObject payload) throws SQLException {
        requireSession(request, "ADMIN");
        String role = optionalString(payload, "role");
        if (role != null && !role.isBlank()
                && !List.of("ADMIN", "BIDDER", "SELLER").contains(role.toUpperCase(Locale.ROOT))) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, "Unknown user role");
        }
        String account = optionalString(payload, "account");
        var users = authService.listUsers().stream()
                .filter(user -> role == null || role.isBlank() || user.role().name().equalsIgnoreCase(role))
                .filter(user -> account == null || user.account().toLowerCase(Locale.ROOT)
                        .contains(account.toLowerCase(Locale.ROOT))).toList();
        return success(request, request.sessionId(), JsonCodec.toJson(paginate(users, payload)));
    }

    private String placeBid(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (placeBidService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Bidding service unavailable");
        }
        SessionManager.UserSession session = requireSession(request, "BIDDER");
        long auctionId = requiredLong(payload, "auctionId");
        long amountCents = requiredLong(payload, "amountCents");
        String clientRequestId = optionalString(payload, "clientRequestId");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            clientRequestId = request.requestId();
        }
        ResponseEnvelope response = placeBidService.placeBid(
                clientRequestId, request.sessionId(), session.userId(), auctionId, amountCents);
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

    private String setAutoBid(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (placeBidService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Bidding service unavailable");
        }
        SessionManager.UserSession session = requireSession(request, "BIDDER");
        long auctionId = requiredLong(payload, "auctionId");
        boolean active = !payload.has("active") || payload.get("active").isJsonNull() || payload.get("active").getAsBoolean();
        long maxAmountCents = active ? requiredLong(payload, "maxAmountCents") : 1L;
        long incrementCents = active ? requiredLong(payload, "incrementCents") : 1L;
        String clientRequestId = optionalString(payload, "clientRequestId");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            clientRequestId = request.requestId();
        }
        ResponseEnvelope response = placeBidService.configureAutoBid(
                clientRequestId, request.sessionId(), session.userId(),
                auctionId, maxAmountCents, incrementCents, active);
        return JsonCodec.toJson(response);
    }

    private String cancelAutoBid(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (placeBidService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Bidding service unavailable");
        }
        SessionManager.UserSession session = requireSession(request, "BIDDER");
        long auctionId = requiredLong(payload, "auctionId");
        String clientRequestId = optionalString(payload, "clientRequestId");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            clientRequestId = request.requestId();
        }
        ResponseEnvelope response = placeBidService.configureAutoBid(
                clientRequestId, request.sessionId(), session.userId(),
                auctionId, 1L, 1L, false);
        return JsonCodec.toJson(response);
    }

    private String settleAuction(RequestEnvelope request, JsonObject payload) throws SQLException {
        if (settlementService == null) {
            return failure(request, ErrorCode.INTERNAL_ERROR, "Settlement service unavailable");
        }
        SessionManager.UserSession session = requireSession(request, null);
        long auctionId = requiredLong(payload, "auctionId");
        String clientRequestId = optionalString(payload, "clientRequestId");
        if (clientRequestId == null || clientRequestId.isBlank()) {
            clientRequestId = request.requestId();
        }
        ResponseEnvelope response = settlementService.settleAuctionRequest(
                clientRequestId, request.sessionId(), session.userId(), session.role(), auctionId);
        return JsonCodec.toJson(response);
    }

    private SessionManager.UserSession requireSession(RequestEnvelope request, String role) {
        SessionManager.UserSession session = sessionManager.getSession(request.sessionId()).orElseThrow(
                () -> new RequestFailure(ErrorCode.AUTHENTICATION_FAILED, "Login is required"));
        if (clientObserver != null) {
            clientObserver.bindUser(session.userId());
        }
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
                case "PING" -> "PONG";
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
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, key + " must be a string");
        }
        return value.getAsString();
    }

    private static String requiredPassword(JsonObject payload) {
        String password = optionalString(payload, "password");
        if (password == null || password.isBlank()) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, "password must not be blank");
        }
        return password;
    }

    private static ItemType itemType(String value) {
        try {
            return ItemType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, "Unknown item type");
        }
    }

    private static long requiredLong(JsonObject payload, String key) {
        try {
            JsonElement value = payload.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            return value.getAsBigDecimal().longValueExact();
        } catch (RuntimeException error) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, key + " must be a number");
        }
    }

    private static long positiveLong(JsonObject payload, String key) {
        long value = requiredLong(payload, key);
        if (value <= 0) {
            throw new RequestFailure(ErrorCode.VALIDATION_ERROR, key + " must be positive");
        }
        return value;
    }

    private static <T> List<T> paginate(List<T> values, JsonObject payload) {
        long page = payload.has("page") ? positiveLong(payload, "page") : 1;
        long size = payload.has("pageSize") ? positiveLong(payload, "pageSize") : Math.max(values.size(), 1);
        // Compare before multiplying so large pages cannot overflow into a negative index.
        if (page - 1 > values.size() / size) {
            return List.of();
        }
        int from = (int) Math.min((page - 1) * size, values.size());
        int to = (int) (from + Math.min(size, values.size() - from));
        return values.subList(from, to);
    }

    private static String success(RequestEnvelope request, String sessionId, String payload) {
        return JsonCodec.toJson(ResponseEnvelope.success(
                request.requestId(), request.action(), sessionId, payload));
    }

    private static String failure(RequestEnvelope request, ErrorCode code, String message) {
        return JsonCodec.toJson(new ResponseEnvelope(
                "v1", request.requestId() == null ? "unknown" : request.requestId(),
                request.action() == null ? "INVALID" : request.action(),
                false, code, message, request.sessionId(), "{}"));
    }

    private static String failureFor(String line, ErrorCode code, String message) {
        try {
            RequestEnvelope request = JsonCodec.fromJson(line, RequestEnvelope.class);
            if (request == null) {
                throw new IllegalArgumentException();
            }
            return failure(request, code, message);
        } catch (RuntimeException ignored) {
            return "{\"protocolVersion\":\"v1\",\"requestId\":\"unknown\","
                    + "\"action\":\"INVALID\",\"ok\":false,\"errorCode\":\""
                    + code + "\",\"message\":\"" + escapeJson(message) + "\",\"sessionId\":null,\"payloadJson\":\"{}\"}";
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "Invalid request" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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