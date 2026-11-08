package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.model.AuctionStatus;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    Optional<AuctionDto> findById(long auctionId) throws SQLException;

    List<AuctionDto> findByStatus(AuctionStatus status) throws SQLException;

    long insert(CreateAuctionCommand command) throws SQLException;

    // Default methods tương thích với ClientHandler cũ
    default List<AuctionDto> findActiveAuctions() throws SQLException {
        return findByStatus(AuctionStatus.OPEN);
    }

    default long createAuction(long itemId, long startPrice, long priceStep, int durationMinutes) throws SQLException {
        Instant now = Instant.now();
        Instant endAt = now.plus(durationMinutes, ChronoUnit.MINUTES);
        return insert(new CreateAuctionCommand(itemId, startPrice, priceStep, now, endAt));
    }
}