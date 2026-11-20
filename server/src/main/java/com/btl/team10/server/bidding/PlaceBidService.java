package com.btl.team10.server.bidding;

import com.btl.team10.common.dto.AutoBidRuleDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ConfigureAutoBidCommand;
import com.btl.team10.common.dto.WalletDto;
import com.btl.team10.common.exception.ValidationException;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.ErrorCode;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.protocol.ResponseEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.network.EventPublisher;
import com.btl.team10.server.repository.AutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcAutoBidRuleRepository;
import com.btl.team10.server.repository.JdbcWalletRepository;
import com.btl.team10.server.repository.WalletRepository;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class PlaceBidService {
    private final Connection connection;
    private final EventPublisher eventPublisher;
    private final AutoBidRuleRepository autoBidRuleRepository;
    private final WalletRepository walletRepository;
    private final AntiSnipePolicy antiSnipePolicy;
    private final ConcurrentHashMap<Long, ReentrantLock> auctionLocks = new ConcurrentHashMap<>();

    public PlaceBidService(Connection connection, EventPublisher eventPublisher) {
        this(connection,
                eventPublisher,
                new JdbcAutoBidRuleRepository(connection),
                new JdbcWalletRepository(connection),
                AntiSnipePolicy.defaultPolicy());
    }

    public PlaceBidService(Connection connection,
                           EventPublisher eventPublisher,
                           AutoBidRuleRepository autoBidRuleRepository,
                           WalletRepository walletRepository,
                           AntiSnipePolicy antiSnipePolicy) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.autoBidRuleRepository = Objects.requireNonNull(autoBidRuleRepository, "autoBidRuleRepository");
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository");
        this.antiSnipePolicy = Objects.requireNonNull(antiSnipePolicy, "antiSnipePolicy");
    }

    public ResponseEnvelope configureAutoBid(String requestId, String sessionId,
                                             long bidderId, long auctionId,
                                             long maxAmountCents, long incrementCents,
                                             boolean active) throws SQLException {
        if (requestId == null || requestId.isBlank() || auctionId <= 0 || bidderId <= 0) {
            return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                    ErrorCode.VALIDATION_ERROR, "Invalid auto-bid parameters");
        }

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            String cached = findReceipt(requestId);
            if (cached != null) {
                return JsonCodec.fromJson(cached, ResponseEnvelope.class);
            }

            boolean prevAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            List<PendingEvent> pendingEvents = new ArrayList<>();
            try {
                AuctionState auction = loadAuction(auctionId);
                if (auction == null) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.NOT_FOUND, "Auction not found");
                }
                if (auction.sellerId == bidderId) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.FORBIDDEN, "Seller cannot configure auto-bid on own auction");
                }

                String nowIso = Instant.now().toString();
                if (!active) {
                    boolean deactivated = autoBidRuleRepository.deactivate(auctionId, bidderId);
                    JsonObject payload = new JsonObject();
                    payload.addProperty("auctionId", auctionId);
                    payload.addProperty("bidderId", bidderId);
                    payload.addProperty("active", false);
                    payload.addProperty("deactivated", deactivated);
                    ResponseEnvelope response = ResponseEnvelope.success(
                            requestId, "SET_AUTO_BID", sessionId, payload.toString());
                    saveReceipt(requestId, "SET_AUTO_BID", JsonCodec.toJson(response), nowIso);
                    connection.commit();
                    return response;
                }

                if (!"OPEN".equals(auction.status) && !"RUNNING".equals(auction.status)) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.AUCTION_NOT_OPEN, "Auction is not open");
                }
                Instant now = Instant.now();
                if (!Instant.parse(auction.endAt).isAfter(now)) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.AUCTION_NOT_OPEN, "Auction has ended");
                }

                ConfigureAutoBidCommand command;
                try {
                    command = new ConfigureAutoBidCommand(auctionId, bidderId, maxAmountCents, incrementCents);
                } catch (ValidationException ex) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.VALIDATION_ERROR, ex.getMessage());
                }

                WalletDto wallet = walletRepository.findByUserId(bidderId).orElse(null);
                if (wallet == null) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.NOT_FOUND, "Wallet not found");
                }
                long currentlyFrozenForAuction = (auction.winnerId != null && auction.winnerId == bidderId)
                        ? auction.currentPriceCents : 0L;
                long available = wallet.balanceCents() - wallet.frozenCents() + currentlyFrozenForAuction;
                long minRequired = auction.winnerId == null
                        ? auction.startPriceCents
                        : auction.currentPriceCents + auction.minIncrementCents;
                if (available < Math.min(maxAmountCents, minRequired)) {
                    connection.rollback();
                    return ResponseEnvelope.failure(requestId, "SET_AUTO_BID",
                            ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance for auto-bid");
                }

                AutoBidRuleDto savedRule = autoBidRuleRepository.upsert(command);

                // Trigger immediate auto-bid evaluation if the auction is live
                if (!Instant.parse(auction.startAt).isAfter(now)) {
                    runAutoBidPolicy(auctionId, now, pendingEvents);
                }

                JsonObject payload = new JsonObject();
                payload.addProperty("ruleId", savedRule.ruleId());
                payload.addProperty("auctionId", savedRule.auctionId());
                payload.addProperty("bidderId", savedRule.bidderId());
                payload.addProperty("maxAmountCents", savedRule.maxAmountCents());
                payload.addProperty("incrementCents", savedRule.incrementCents());
                payload.addProperty("registeredAt", savedRule.registeredAt().toString());
                payload.addProperty("active", savedRule.active());

                ResponseEnvelope response = ResponseEnvelope.success(
                        requestId, "SET_AUTO_BID", sessionId, payload.toString());
                saveReceipt(requestId, "SET_AUTO_BID", JsonCodec.toJson(response), now.toString());

                connection.commit();
                publishAll(pendingEvents);
                return response;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(prevAutoCommit);
            }
        } finally {
            lock.unlock();
        }
    }

    public ResponseEnvelope placeBid(String requestId, String sessionId, long bidderId, long auctionId, long amountCents) throws SQLException {
        if (requestId == null || requestId.isBlank() || auctionId <= 0 || amountCents <= 0) {
            return reject(requestId, auctionId, bidderId, ErrorCode.VALIDATION_ERROR, "Invalid bid parameters");
        }

        ReentrantLock lock = auctionLocks.computeIfAbsent(auctionId, k -> new ReentrantLock());
        lock.lock();
        try {
            String cached = findReceipt(requestId);
            if (cached != null) {
                return JsonCodec.fromJson(cached, ResponseEnvelope.class);
            }

            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            List<PendingEvent> pendingEvents = new ArrayList<>();
            try {
                AuctionState auction = loadAuction(auctionId);
                if (auction == null) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.NOT_FOUND, "Auction not found");
                }
                if (!"OPEN".equals(auction.status) && !"RUNNING".equals(auction.status)) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.AUCTION_NOT_OPEN, "Auction is not open");
                }
                Instant now = Instant.now();
                if (Instant.parse(auction.startAt).isAfter(now)) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.AUCTION_NOT_OPEN, "Auction has not started");
                }
                if (!Instant.parse(auction.endAt).isAfter(now)) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.AUCTION_NOT_OPEN, "Auction has ended");
                }
                if (auction.sellerId == bidderId) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.FORBIDDEN, "Seller cannot bid on own auction");
                }

                long minRequired = auction.winnerId == null
                        ? auction.startPriceCents
                        : auction.currentPriceCents + auction.minIncrementCents;
                if (amountCents < minRequired) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.BID_TOO_LOW,
                            "Bid amount is below minimum required: " + minRequired);
                }

                WalletDto bidderWallet = walletRepository.findByUserId(bidderId).orElse(null);
                if (bidderWallet == null) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.NOT_FOUND, "Wallet not found");
                }
                long currentlyFrozenForThisAuction = (auction.winnerId != null && auction.winnerId == bidderId)
                        ? auction.currentPriceCents
                        : 0L;
                long available = bidderWallet.balanceCents() - bidderWallet.frozenCents() + currentlyFrozenForThisAuction;
                if (available < amountCents) {
                    connection.rollback();
                    return reject(requestId, auctionId, bidderId, ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
                }

                long bidId = executeAcceptedBid(auction, bidderId, amountCents, requestId, now, pendingEvents);

                JsonObject responsePayload = new JsonObject();
                responsePayload.addProperty("auctionId", auctionId);
                responsePayload.addProperty("bidId", bidId);
                responsePayload.addProperty("bidderId", bidderId);
                responsePayload.addProperty("amountCents", amountCents);
                responsePayload.addProperty("currentPriceCents", amountCents);
                responsePayload.addProperty("auctionVersion", auction.version + 1L);
                responsePayload.addProperty("bidTime", now.toString());
                responsePayload.addProperty("status", BidStatus.ACTIVE.name());
                responsePayload.addProperty("clientRequestId", requestId);
                ResponseEnvelope response = ResponseEnvelope.success(requestId, "PLACE_BID", sessionId, responsePayload.toString());
                saveReceipt(requestId, "PLACE_BID", JsonCodec.toJson(response), now.toString());

                // Trigger Auto-Bid counter-bidding policy after manual bid succeeds
                runAutoBidPolicy(auctionId, now, pendingEvents);

                connection.commit();
                publishAll(pendingEvents);
                return response;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } finally {
            lock.unlock();
        }
    }

    private long executeAcceptedBid(AuctionState auction, long bidderId, long amountCents,
                                    String requestId, Instant now,
                                    List<PendingEvent> pendingEvents) throws SQLException {
        long auctionId = auction.auctionId;
        Instant currentEndAt = Instant.parse(auction.endAt);
        boolean extend = antiSnipePolicy.shouldExtend(now, currentEndAt, auction.extensionCount);
        Instant newEndAt = extend ? currentEndAt.plusSeconds(antiSnipePolicy.extensionSeconds()) : currentEndAt;
        int newExtensionCount = extend ? auction.extensionCount + 1 : auction.extensionCount;

        int updatedAuction = updateAuctionOptimistic(
                auctionId, amountCents, bidderId, newEndAt.toString(), newExtensionCount, auction.version);
        if (updatedAuction == 0) {
            throw new SQLException("Concurrent bid conflict on auction " + auctionId);
        }

        long newAuctionVersion = auction.version + 1L;
        markPreviousBidsOutbid(auctionId);
        String nowIso = now.toString();
        long bidId = insertBid(auctionId, bidderId, amountCents, nowIso, requestId);
        insertBidHistory(bidId, auctionId, bidderId, amountCents, nowIso, requestId, newAuctionVersion);

        WalletDto bidderWallet = walletRepository.findByUserId(bidderId)
                .orElseThrow(() -> new SQLException("Bidder wallet not found"));
        if (auction.winnerId != null && auction.winnerId == bidderId) {
            long delta = amountCents - auction.currentPriceCents;
            if (!walletRepository.adjustFrozen(bidderId, delta, bidderWallet.version())) {
                throw new SQLException("Wallet update conflict for current leader");
            }
            WalletDto refreshed = walletRepository.findByUserId(bidderId).orElseThrow();
            recordBalanceChanged(auctionId, refreshed, nowIso, pendingEvents);
        } else {
            if (!walletRepository.adjustFrozen(bidderId, amountCents, bidderWallet.version())) {
                throw new SQLException("Wallet update conflict for new bidder");
            }
            WalletDto refreshedNew = walletRepository.findByUserId(bidderId).orElseThrow();
            recordBalanceChanged(auctionId, refreshedNew, nowIso, pendingEvents);

            if (auction.winnerId != null) {
                WalletDto prevWallet = walletRepository.findByUserId(auction.winnerId)
                        .orElseThrow(() -> new SQLException("Previous leader wallet not found"));
                if (!walletRepository.adjustFrozen(auction.winnerId, -auction.currentPriceCents, prevWallet.version())) {
                    throw new SQLException("Previous bidder wallet conflict");
                }
                WalletDto refreshedPrev = walletRepository.findByUserId(auction.winnerId).orElseThrow();
                recordBalanceChanged(auctionId, refreshedPrev, nowIso, pendingEvents);
            }
        }

        long seq = nextSequence(auctionId);
        JsonObject eventPayload = new JsonObject();
        eventPayload.addProperty("bidId", bidId);
        eventPayload.addProperty("auctionId", auctionId);
        eventPayload.addProperty("bidderId", bidderId);
        eventPayload.addProperty("amountCents", amountCents);
        eventPayload.addProperty("currentPriceCents", amountCents);
        eventPayload.addProperty("auctionVersion", newAuctionVersion);
        eventPayload.addProperty("clientRequestId", requestId);
        eventPayload.addProperty("status", BidStatus.ACTIVE.name());
        eventPayload.addProperty("bidTime", nowIso);
        insertEvent(auctionId, seq, EventType.BID_ACCEPTED.name(), eventPayload.toString(), nowIso);
        pendingEvents.add(PendingEvent.toAll(EventType.BID_ACCEPTED, auctionId, seq, eventPayload.toString()));

        if (auction.winnerId != null && auction.winnerId != bidderId) {
            long outbidSeq = nextSequence(auctionId);
            JsonObject outbidPayload = new JsonObject();
            outbidPayload.addProperty("auctionId", auctionId);
            outbidPayload.addProperty("newPriceCents", amountCents);
            outbidPayload.addProperty("currentPriceCents", amountCents);
            outbidPayload.addProperty("newLeaderId", bidderId);
            outbidPayload.addProperty("auctionVersion", newAuctionVersion);
            outbidPayload.addProperty("bidId", bidId);
            outbidPayload.addProperty("clientRequestId", requestId);
            insertEvent(auctionId, outbidSeq, EventType.OUTBID.name(), outbidPayload.toString(), nowIso);
            pendingEvents.add(PendingEvent.toUser(auction.winnerId, EventType.OUTBID,
                    auctionId, outbidSeq, outbidPayload.toString()));
        }

        if (extend) {
            long extSeq = nextSequence(auctionId);
            JsonObject extPayload = new JsonObject();
            extPayload.addProperty("auctionId", auctionId);
            extPayload.addProperty("oldEndAt", currentEndAt.toString());
            extPayload.addProperty("newEndAt", newEndAt.toString());
            extPayload.addProperty("extensionCount", newExtensionCount);
            extPayload.addProperty("maxExtensions", antiSnipePolicy.maxExtensions());
            extPayload.addProperty("extendedBySeconds", antiSnipePolicy.extensionSeconds());
            extPayload.addProperty("triggeredByBidId", bidId);
            extPayload.addProperty("auctionVersion", newAuctionVersion);
            insertEvent(auctionId, extSeq, EventType.AUCTION_EXTENDED.name(), extPayload.toString(), nowIso);
            pendingEvents.add(PendingEvent.toAll(EventType.AUCTION_EXTENDED, auctionId, extSeq, extPayload.toString()));
        }

        return bidId;
    }

    private void runAutoBidPolicy(long auctionId, Instant now, List<PendingEvent> pendingEvents) throws SQLException {
        for (int round = 0; round < 32; round++) {
            AuctionState auction = loadAuction(auctionId);
            if (auction == null || (!"OPEN".equals(auction.status) && !"RUNNING".equals(auction.status))) {
                return;
            }
            if (!Instant.parse(auction.endAt).isAfter(now)) {
                return;
            }

            List<AutoBidRuleDto> activeRules = autoBidRuleRepository.findActiveByAuction(auctionId);
            if (activeRules.isEmpty()) {
                return;
            }

            Comparator<AutoBidRuleDto> rulePriority = Comparator
                    .comparingLong(AutoBidRuleDto::maxAmountCents).reversed()
                    .thenComparing(AutoBidRuleDto::registeredAt)
                    .thenComparingLong(AutoBidRuleDto::ruleId);

            List<AutoBidRuleDto> eligible = new ArrayList<>();
            for (AutoBidRuleDto rule : activeRules) {
                if (rule.bidderId() == auction.sellerId) {
                    continue;
                }
                if (auction.winnerId != null && rule.bidderId() == auction.winnerId) {
                    continue;
                }
                long minNext = requiredNextBidForRule(auction, rule);
                if (rule.maxAmountCents() < minNext) {
                    continue;
                }
                WalletDto wallet = walletRepository.findByUserId(rule.bidderId()).orElse(null);
                if (wallet == null || (wallet.balanceCents() - wallet.frozenCents()) < minNext) {
                    continue;
                }
                eligible.add(rule);
            }

            if (eligible.isEmpty()) {
                return;
            }

            eligible.sort(rulePriority);
            AutoBidRuleDto topRule = eligible.get(0);
            long minNext = requiredNextBidForRule(auction, topRule);

            AutoBidRuleDto bestCompetitor = null;
            if (auction.winnerId != null) {
                Optional<AutoBidRuleDto> leaderRule = autoBidRuleRepository.findByAuctionAndBidder(auctionId, auction.winnerId);
                if (leaderRule.isPresent() && leaderRule.get().active()) {
                    bestCompetitor = leaderRule.get();
                }
            }
            if (eligible.size() > 1) {
                AutoBidRuleDto secondEligible = eligible.get(1);
                if (bestCompetitor == null || rulePriority.compare(secondEligible, bestCompetitor) < 0) {
                    bestCompetitor = secondEligible;
                }
            }

            long targetBid;
            if (bestCompetitor != null && bestCompetitor.maxAmountCents() >= minNext) {
                long competingCap = bestCompetitor.maxAmountCents();
                boolean topHasHigherPriority = rulePriority.compare(topRule, bestCompetitor) < 0;
                if (competingCap < topRule.maxAmountCents()) {
                    long stepOverCompetitor = competingCap + Math.max(auction.minIncrementCents, topRule.incrementCents());
                    targetBid = Math.min(topRule.maxAmountCents(), Math.max(minNext, stepOverCompetitor));
                } else if (competingCap == topRule.maxAmountCents() && topHasHigherPriority) {
                    targetBid = topRule.maxAmountCents();
                } else {
                    // Competitor registered earlier (or has higher cap); push price up to just below cap so competitor counters to win
                    long stepBeforeCap = competingCap - Math.max(auction.minIncrementCents, bestCompetitor.incrementCents());
                    targetBid = Math.max(minNext, Math.min(topRule.maxAmountCents(), stepBeforeCap));
                }
            } else {
                targetBid = minNext;
            }

            WalletDto topWallet = walletRepository.findByUserId(topRule.bidderId()).orElseThrow();
            long maxAffordable = topWallet.balanceCents() - topWallet.frozenCents();
            targetBid = Math.min(targetBid, maxAffordable);
            if (targetBid < minNext) {
                return;
            }

            String autoRequestId = "autobid-" + topRule.ruleId() + "-v" + (auction.version + 1L);
            executeAcceptedBid(auction, topRule.bidderId(), targetBid, autoRequestId, now, pendingEvents);
        }
    }

    private long requiredNextBidForRule(AuctionState auction, AutoBidRuleDto rule) {
        if (auction.winnerId == null) {
            return auction.startPriceCents;
        }
        long effectiveIncrement = Math.max(auction.minIncrementCents, rule.incrementCents());
        return auction.currentPriceCents + effectiveIncrement;
    }

    private void recordBalanceChanged(long auctionId, WalletDto wallet, String nowIso,
                                      List<PendingEvent> pendingEvents) throws SQLException {
        long seq = nextSequence(auctionId);
        JsonObject payload = new JsonObject();
        payload.addProperty("auctionId", auctionId);
        payload.addProperty("userId", wallet.userId());
        payload.addProperty("balanceCents", wallet.balanceCents());
        payload.addProperty("frozenCents", wallet.frozenCents());
        payload.addProperty("availableCents", wallet.balanceCents() - wallet.frozenCents());
        payload.addProperty("version", wallet.version());
        insertEvent(auctionId, seq, EventType.BALANCE_CHANGED.name(), payload.toString(), nowIso);
        pendingEvents.add(PendingEvent.toUser(wallet.userId(), EventType.BALANCE_CHANGED,
                auctionId, seq, payload.toString()));
    }

    private void publishAll(List<PendingEvent> events) {
        for (PendingEvent event : events) {
            if (event.targetUserId == null) {
                eventPublisher.publishToAll(event.type, event.auctionId, event.sequence, event.payloadJson);
            } else {
                eventPublisher.publishToUser(event.targetUserId, event.type, event.auctionId, event.sequence, event.payloadJson);
            }
        }
    }

    public List<BidDto> listBidsByAuction(long auctionId) throws SQLException {
        String sql = "SELECT bid_id, auction_id, bidder_id, amount_cents, bid_time, status, client_request_id "
                + "FROM bids WHERE auction_id = ? ORDER BY amount_cents DESC, bid_time ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<BidDto> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new BidDto(
                            rs.getLong("bid_id"),
                            rs.getLong("auction_id"),
                            rs.getLong("bidder_id"),
                            rs.getLong("amount_cents"),
                            Instant.parse(rs.getString("bid_time")),
                            BidStatus.valueOf(rs.getString("status")),
                            rs.getString("client_request_id")
                    ));
                }
                return list;
            }
        }
    }

    private ResponseEnvelope reject(String requestId, long auctionId, long bidderId,
                                    ErrorCode errorCode, String message) {
        ResponseEnvelope response = ResponseEnvelope.failure(requestId, "PLACE_BID", errorCode, message);
        if (auctionId > 0 && bidderId > 0) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("requestId", requestId);
                payload.addProperty("auctionId", auctionId);
                payload.addProperty("userId", bidderId);
                payload.addProperty("errorCode", errorCode.name());
                payload.addProperty("reason", message);
                eventPublisher.publishToUser(bidderId, EventType.BID_REJECTED,
                        auctionId, currentSequence(auctionId), payload.toString());
            } catch (SQLException ignored) {
                // The response still carries the rejection when the audit sequence cannot be read.
            }
        }
        return response;
    }

    private long currentSequence(long auctionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence), 1) FROM auction_events WHERE auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(1L, rs.getLong(1)) : 1L;
            }
        }
    }

    private String findReceipt(String requestId) throws SQLException {
        String sql = "SELECT response_json FROM request_receipts WHERE request_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("response_json") : null;
            }
        }
    }

    private void saveReceipt(String requestId, String action, String responseJson, String createdAt) throws SQLException {
        String sql = "INSERT INTO request_receipts (request_id, action, response_json, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, action);
            ps.setString(3, responseJson);
            ps.setString(4, createdAt);
            ps.executeUpdate();
        }
    }

    private AuctionState loadAuction(long auctionId) throws SQLException {
        String sql = "SELECT a.auction_id, a.start_price_cents, a.current_price_cents, a.min_increment_cents, "
                + "a.start_at, a.end_at, a.status, a.version, a.extension_count, a.winner_id, i.seller_id "
                + "FROM auctions a JOIN items i ON a.item_id = i.item_id WHERE a.auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long rawWinner = rs.getLong("winner_id");
                Long winnerId = rs.wasNull() ? null : rawWinner;
                return new AuctionState(
                        rs.getLong("auction_id"),
                        rs.getLong("start_price_cents"),
                        rs.getLong("current_price_cents"),
                        rs.getLong("min_increment_cents"),
                        rs.getString("start_at"),
                        rs.getString("end_at"),
                        rs.getString("status"),
                        rs.getInt("version"),
                        rs.getInt("extension_count"),
                        winnerId,
                        rs.getLong("seller_id")
                );
            }
        }
    }

    private int updateAuctionOptimistic(long auctionId, long newPrice, long winnerId,
                                        String newEndAt, int newExtensionCount,
                                        int expectedVersion) throws SQLException {
        String sql = "UPDATE auctions SET current_price_cents = ?, winner_id = ?, end_at = ?, "
                + "extension_count = ?, status = 'RUNNING', version = version + 1 "
                + "WHERE auction_id = ? AND version = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newPrice);
            ps.setLong(2, winnerId);
            ps.setString(3, newEndAt);
            ps.setInt(4, newExtensionCount);
            ps.setLong(5, auctionId);
            ps.setInt(6, expectedVersion);
            return ps.executeUpdate();
        }
    }

    private void markPreviousBidsOutbid(long auctionId) throws SQLException {
        String sql = "UPDATE bids SET status = 'OUTBID' WHERE auction_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.executeUpdate();
        }
    }

    private long insertBid(long auctionId, long bidderId, long amountCents, String bidTime, String requestId) throws SQLException {
        String sql = "INSERT INTO bids (auction_id, bidder_id, amount_cents, bid_time, status, client_request_id) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, bidderId);
            ps.setLong(3, amountCents);
            ps.setString(4, bidTime);
            ps.setString(5, requestId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Failed to retrieve generated bid_id");
            }
        }
    }

    private void insertBidHistory(long bidId, long auctionId, long bidderId, long amountCents,
                                  String bidTime, String requestId, long auctionVersion) throws SQLException {
        String sql = "INSERT INTO bid_history (bid_id, auction_id, bidder_id, amount_cents, bid_time, status, "
                + "client_request_id, auction_version) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, bidId);
            ps.setLong(2, auctionId);
            ps.setLong(3, bidderId);
            ps.setLong(4, amountCents);
            ps.setString(5, bidTime);
            ps.setString(6, requestId);
            ps.setLong(7, auctionVersion);
            ps.executeUpdate();
        }
    }

    private long nextSequence(long auctionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence), 0) + 1 FROM auction_events WHERE auction_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 1L;
            }
        }
    }

    private void insertEvent(long auctionId, long sequence, String eventType, String payloadJson, String occurredAt) throws SQLException {
        String sql = "INSERT INTO auction_events (auction_id, sequence, event_type, payload_json, occurred_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.setLong(2, sequence);
            ps.setString(3, eventType);
            ps.setString(4, payloadJson);
            ps.setString(5, occurredAt);
            ps.executeUpdate();
        }
    }

    private record AuctionState(
            long auctionId,
            long startPriceCents,
            long currentPriceCents,
            long minIncrementCents,
            String startAt,
            String endAt,
            String status,
            int version,
            int extensionCount,
            Long winnerId,
            long sellerId
    ) {}

    private record PendingEvent(Long targetUserId, EventType type, long auctionId, long sequence, String payloadJson) {
        static PendingEvent toAll(EventType type, long auctionId, long sequence, String payloadJson) {
            return new PendingEvent(null, type, auctionId, sequence, payloadJson);
        }

        static PendingEvent toUser(long userId, EventType type, long auctionId, long sequence, String payloadJson) {
            return new PendingEvent(userId, type, auctionId, sequence, payloadJson);
        }
    }
}