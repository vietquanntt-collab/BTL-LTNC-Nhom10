package com.btl.team10.server.repository;

import com.btl.team10.common.dto.WalletDto;

import java.sql.SQLException;
import java.util.Optional;

public interface WalletRepository {
    Optional<WalletDto> findByUserId(long userId) throws SQLException;

    boolean adjustFrozen(long userId, long deltaCents, long expectedVersion) throws SQLException;

    boolean credit(long userId, long amountCents, long expectedVersion) throws SQLException;
}
