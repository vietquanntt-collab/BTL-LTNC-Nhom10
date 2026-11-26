package com.btl.team10.server.repository;

import com.btl.team10.common.dto.AutoBidRuleDto;
import com.btl.team10.common.dto.ConfigureAutoBidCommand;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AutoBidRuleRepository {
    Optional<AutoBidRuleDto> findByAuctionAndBidder(long auctionId, long bidderId) throws SQLException;

    List<AutoBidRuleDto> findActiveByAuction(long auctionId) throws SQLException;

    AutoBidRuleDto upsert(ConfigureAutoBidCommand command) throws SQLException;

    boolean deactivate(long auctionId, long bidderId) throws SQLException;
}
