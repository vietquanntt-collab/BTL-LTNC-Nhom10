package com.btl.team10.server.session;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    public record UserSession(String token, long userId, String account, String role, Instant createdAt) {}

    // Dung ConcurrentHashMap de dam bao an toan da luong khi nhieu client ket noi dong thoi
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public UserSession createSession(long userId, String account, String role) {
        String token = UUID.randomUUID().toString();
        UserSession session = new UserSession(token, userId, account, role, Instant.now());
        sessions.put(token, session);
        return session;
    }

    public Optional<UserSession> getSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token));
    }

    public void removeSession(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public boolean hasRole(String token, String requiredRole) {
        return getSession(token)
                .map(s -> s.role().equalsIgnoreCase(requiredRole))
                .orElse(false);
    }
}