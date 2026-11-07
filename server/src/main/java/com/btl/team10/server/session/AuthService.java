package com.btl.team10.server.session;

import com.btl.team10.server.repository.UserRepository;

import java.util.Optional;

public class AuthService {

    private final UserRepository userRepository;
    private final SessionManager sessionManager;

    public AuthService(UserRepository userRepository, SessionManager sessionManager) {
        this.userRepository = userRepository;
        this.sessionManager = sessionManager;
    }

    public Optional<SessionManager.UserSession> login(String account, String password) {
        var userOpt = userRepository.findByAccount(account);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        var user = userOpt.get();
        // Giai doan tuan 2: kiem tra plain text hoac hash don gian
        if (!user.passwordHash().equals(password)) {
            return Optional.empty();
        }

        return Optional.of(sessionManager.createSession(user.userId(), user.account(), user.role()));
    }
}