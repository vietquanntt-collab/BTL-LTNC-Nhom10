package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.CreateAuctionCommand;
import com.btl.team10.common.model.AuctionStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    Optional<AuctionDto> findById(long auctionId) throws SQLException;

    List<AuctionDto> findByStatus(AuctionStatus status) throws SQLException;

    long insert(CreateAuctionCommand command) throws SQLException;
}
