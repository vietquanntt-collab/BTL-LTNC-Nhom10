package com.btl.team10.server.network;

import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.common.protocol.RequestEnvelope;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.server.persistence.DatabaseConnectionFactory;
import com.btl.team10.server.persistence.DatabaseInitializer;
import com.btl.team10.server.repository.JdbcAuctionRepository;
import com.btl.team10.server.repository.JdbcItemRepository;
import com.btl.team10.server.repository.JdbcUserRepository;
import com.btl.team10.server.session.AuthService;
import com.btl.team10.server.session.PasswordHasher;
import com.btl.team10.server.session.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SocketIntegrationTest {
    @Test
    void loginCreateItemCreateAuctionAndListUseTheJsonContract() throws Exception {
        Path databaseRoot = databaseRoot();
        try (Connection connection = DatabaseConnectionFactory.open("jdbc:sqlite::memory:")) {
            DatabaseInitializer.initialize(connection, databaseRoot, false);
            JdbcUserRepository users = new JdbcUserRepository(connection);
            users.insert("socket-seller", PasswordHasher.hash("secret123"),
                    com.btl.team10.common.model.Role.SELLER);
            SessionManager sessions = new SessionManager();
            AuctionSocketServer boundServer = new AuctionSocketServer(
                    0, new AuthService(users, sessions), sessions,
                        new JdbcItemRepository(connection), new JdbcAuctionRepository(connection));
            Thread serverThread = new Thread(() -> {
                try {
                    boundServer.start();
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();
            int port = waitForPort(boundServer);

            try (Socket client = new Socket("127.0.0.1", port);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    ResponseEnvelope login = send(writer, reader, "LOGIN", null,
                            object("account", "socket-seller", "password", "secret123"));
                    assertTrue(login.ok());
                    String session = login.sessionId();

                    ResponseEnvelope item = send(writer, reader, "CREATE_ITEM", session,
                            object("name", "Socket item", "itemType", "CARD", "description", "demo"));
                    assertTrue(item.ok());
                    long itemId = JsonParser.parseString(item.payloadJson()).getAsJsonObject()
                            .get("itemId").getAsLong();

                    ResponseEnvelope auction = send(writer, reader, "CREATE_AUCTION", session,
                            object("itemId", itemId, "startPriceCents", 1000,
                                    "minIncrementCents", 50, "durationHours", 1));
                    assertTrue(auction.ok());

                    ResponseEnvelope list = send(writer, reader, "LIST_AUCTIONS", session, new JsonObject());
                    assertTrue(list.ok());
                    assertTrue(list.payloadJson().contains("auctionId"));
            } finally {
                boundServer.stop();
            }
        }
    }

    private static ResponseEnvelope send(PrintWriter writer, BufferedReader reader, String action,
                                        String session, JsonObject payload) throws Exception {
        writer.println(JsonCodec.toJson(new RequestEnvelope(
                "v1", UUID.randomUUID().toString(), action, session, payload.toString())));
        return JsonCodec.fromJson(reader.readLine(), ResponseEnvelope.class);
    }

    private static JsonObject object(Object... values) {
        JsonObject result = new JsonObject();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value instanceof Number number) {
                result.addProperty(String.valueOf(values[index]), number);
            } else {
                result.addProperty(String.valueOf(values[index]), String.valueOf(value));
            }
        }
        return result;
    }

    private static int waitForPort(AuctionSocketServer server) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            int port = server.boundPort();
            if (port > 0) {
                try (Socket ignored = new Socket("127.0.0.1", port)) {
                    return port;
                } catch (Exception ignored) {
                    // The server has bound the port but has not entered accept yet.
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
        }
        throw new IllegalStateException("Socket server did not start");
    }

    private static Path databaseRoot() {
        Path fromRepository = Path.of("database");
        return java.nio.file.Files.isDirectory(fromRepository.resolve("migrations"))
                ? fromRepository.toAbsolutePath().normalize()
                : Path.of("..", "database").toAbsolutePath().normalize();
    }
}
