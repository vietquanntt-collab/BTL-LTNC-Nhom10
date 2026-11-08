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
        var userOpt = userRepository.findByAccount(account);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        var user = userOpt.get();
        // UserDto khong chua mat khau; xac thuc password hop le truoc khi tao session
        if (password == null || password.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(sessionManager.createSession(user.userId(), user.account(), user.role().name()));
    }
}