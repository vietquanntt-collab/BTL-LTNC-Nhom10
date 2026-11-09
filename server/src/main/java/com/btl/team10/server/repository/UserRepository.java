package com.btl.team10.server.repository;

import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.Role;

import java.sql.SQLException;
import java.util.Optional;

public interface UserRepository {
    Optional<UserDto> findById(long userId) throws SQLException;

    Optional<UserDto> findByAccount(String account) throws SQLException;

    String findPasswordHashByAccount(String account) throws SQLException;

    long insert(String account, String passwordHash, Role role) throws SQLException;
}
