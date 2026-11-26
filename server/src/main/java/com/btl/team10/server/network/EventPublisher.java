package com.btl.team10.server.network;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.server.event.AuctionEventObserver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventPublisher {
    private final List<AuctionEventObserver> observers = new CopyOnWriteArrayList<>();
    private final Object publishLock = new Object();
    private final ThreadLocal<List<QueuedEvent>> deferredBatch = new ThreadLocal<>();

    public void beginDeferredBatch() {
        deferredBatch.set(new ArrayList<>());
    }

    public void flushDeferredBatch() {
        List<QueuedEvent> queued = deferredBatch.get();
        deferredBatch.remove();
        if (queued == null || queued.isEmpty()) {
            return;
        }
        for (QueuedEvent item : queued) {
            deliver(item.envelope, item.targetUserId);
        }
    }

    public void discardDeferredBatch() {
        deferredBatch.remove();
    }

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
        List<QueuedEvent> queued = deferredBatch.get();
        if (queued != null) {
            queued.add(new QueuedEvent(envelope, targetUserId));
            return;
        }
        deliver(envelope, targetUserId);
    }

    private void deliver(EventEnvelope envelope, Long targetUserId) {
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

    private record QueuedEvent(EventEnvelope envelope, Long targetUserId) {
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
