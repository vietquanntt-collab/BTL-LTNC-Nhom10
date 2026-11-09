package com.btl.team10.server.session;

import com.btl.team10.server.repository.UserRepository;

import java.util.Optional;
import java.sql.SQLException;

public class AuthService {

    private final UserRepository userRepository;
    private final SessionManager sessionManager;

    public AuthService(UserRepository userRepository, SessionManager sessionManager) {
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
    }

    public Optional<SessionManager.UserSession> login(String account, String password) throws SQLException {
        if (account == null || account.isBlank() || password == null || password.isBlank()) {
            return Optional.empty();
        }
        var userOpt = userRepository.findByAccount(account);
        String passwordHash = userRepository.findPasswordHashByAccount(account);
        if (userOpt.isEmpty() || !PasswordHasher.verify(password, passwordHash)) {
            return Optional.empty();
        }
        var user = userOpt.get();
        return Optional.of(sessionManager.createSession(user.userId(), user.account(), user.role().name()));
    }

    public void logout(String token) {
        sessionManager.removeSession(token);
    }
}
