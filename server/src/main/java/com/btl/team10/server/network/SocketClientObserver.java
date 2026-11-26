package com.btl.team10.server.network;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.serialization.JsonCodec;
import com.btl.team10.server.event.AuctionEventObserver;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class SocketClientObserver implements AuctionEventObserver {
    private final PrintWriter writer;
    private final Deque<String> deferredEvents = new ArrayDeque<>();
    private volatile Long boundUserId;
    private boolean handlingRequest;

    public SocketClientObserver(PrintWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public void bindUser(Long userId) {
        this.boundUserId = userId;
    }

    public void beginRequest() {
        synchronized (writer) {
            handlingRequest = true;
        }
    }

    public void finishRequestAndFlush(String responseLine) {
        synchronized (writer) {
            writer.println(responseLine);
            handlingRequest = false;
            while (!deferredEvents.isEmpty()) {
                writer.println(deferredEvents.removeFirst());
            }
        }
    }

    @Override
    public void onEvent(EventEnvelope envelope, Long targetUserId) {
        if (targetUserId != null && (boundUserId == null || !targetUserId.equals(boundUserId))) {
            return;
        }
        String line = JsonCodec.toJson(envelope);
        synchronized (writer) {
            if (handlingRequest) {
                deferredEvents.addLast(line);
            } else {
                writer.println(line);
            }
        }
    }
}
