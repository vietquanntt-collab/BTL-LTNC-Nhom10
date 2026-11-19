package com.btl.team10.server.repository;

import com.btl.team10.common.dto.SettleAuctionCommand;
import com.btl.team10.common.dto.SettlementDto;
import com.btl.team10.common.dto.SettlementWriteResult;

import java.sql.SQLException;
import java.util.Optional;

public interface SettlementRepository {
    Optional<SettlementDto> findByAuctionId(long auctionId) throws SQLException;

    SettlementWriteResult settle(SettleAuctionCommand command) throws SQLException;
}
