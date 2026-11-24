package com.btl.team10.server.session;

import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.Role;
import com.btl.team10.server.repository.UserRepository;

import java.util.List;
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
        var user = com.btl.team10.server.domain.User.from(userOpt.get());
        return Optional.of(sessionManager.createSession(user.getId(), user.getAccount(), user.getRole().name()));
    }

    public void logout(String token) {
        sessionManager.removeSession(token);
    }

    public long registerBidder(String account, String password) throws SQLException {
        if (account == null || account.isBlank() || account.trim().length() > 64) {
            throw new IllegalArgumentException("account must contain 1 to 64 non-blank characters");
        }
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 128) {
            throw new IllegalArgumentException("password must contain 6 to 128 characters");
        }
        if (userRepository.findByAccount(account).isPresent()) {
            throw new IllegalArgumentException("account is already registered");
        }
        return userRepository.insert(account.trim(), PasswordHasher.hash(password), Role.BIDDER);
    }

    public Optional<UserDto> findUser(long userId) throws SQLException {
        return userRepository.findById(userId);
    }

    public List<UserDto> listUsers() throws SQLException {
        return userRepository.findAll();
    }
}
