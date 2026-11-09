package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.model.AuctionStatus;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

public interface AuctionRepository {
    Optional<AuctionDto> findById(long auctionId) throws SQLException;

    List<AuctionDto> findByStatus(AuctionStatus status) throws SQLException;

    long insert(CreateAuctionCommand command) throws SQLException;

    default List<AuctionDto> findActiveAuctions() throws SQLException {
        List<AuctionDto> active = new ArrayList<>(findByStatus(AuctionStatus.OPEN));
        active.addAll(findByStatus(AuctionStatus.RUNNING));
        return List.copyOf(active);
    }

    default long createAuction(long itemId, long startPrice, long priceStep, int durationHours) throws SQLException {
        Instant now = Instant.now();
        if (durationHours < 1) {
            throw new IllegalArgumentException("durationHours must be at least 1");
        }
        Instant endAt = now.plus(durationHours, ChronoUnit.HOURS);
        return insert(new CreateAuctionCommand(itemId, startPrice, priceStep, now, endAt));
    }
}
