package com.btl.team10.server.event;

import com.btl.team10.common.protocol.EventEnvelope;

@FunctionalInterface
public interface AuctionEventObserver {
    void onEvent(EventEnvelope envelope, Long targetUserId);
}