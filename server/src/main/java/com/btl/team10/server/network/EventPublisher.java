package com.btl.team10.server.network;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.server.event.AuctionEventObserver;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventPublisher {
    private final List<AuctionEventObserver> observers = new CopyOnWriteArrayList<>();
    private final Object publishLock = new Object();

    public void subscribe(AuctionEventObserver observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(AuctionEventObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }

    public void publishToAll(EventType type, long auctionId, long sequence, String payloadJson) {
        publish(createEnvelope(type, auctionId, sequence, payloadJson), null);
    }

    public void publishToUser(long userId, EventType type, long auctionId, long sequence, String payloadJson) {
        publish(createEnvelope(type, auctionId, sequence, payloadJson), userId);
    }

    private void publish(EventEnvelope envelope, Long targetUserId) {
        synchronized (publishLock) {
            for (AuctionEventObserver observer : observers) {
                try {
                    observer.onEvent(envelope, targetUserId);
                } catch (Exception ignored) {
                    // Isolate observer failures so other observers still receive events.
                }
            }
        }
    }

    private EventEnvelope createEnvelope(EventType type, long auctionId, long sequence, String payloadJson) {
        return new EventEnvelope(
                "v1",
                UUID.randomUUID().toString(),
                type,
                auctionId,
                sequence,
                Instant.now(),
                payloadJson
        );
    }
}
