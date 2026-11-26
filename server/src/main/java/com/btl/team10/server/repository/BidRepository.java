package com.btl.team10.server.repository;

import com.btl.team10.common.dto.BidHistoryDto;
import com.btl.team10.common.dto.BidWriteResult;
import com.btl.team10.common.dto.PlaceBidCommand;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Data boundary for the atomic place-bid write used by week 3 services. */
public interface BidRepository {
    /**
     * Places one bid, updates the auction version and appends one history row
     * in the same transaction. A repeated client request is idempotent.
     */
    BidWriteResult placeBid(PlaceBidCommand command) throws SQLException;

    List<BidHistoryDto> findHistory(long auctionId) throws SQLException;

    Optional<BidWriteResult> findByRequest(long auctionId, long bidderId,
                                           String clientRequestId) throws SQLException;
}
