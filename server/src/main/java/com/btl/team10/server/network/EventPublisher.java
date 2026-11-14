package com.btl.team10.server.network;

import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.btl.team10.common.serialization.JsonCodec;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventPublisher {
    private final Set<ClientChannel> channels = ConcurrentHashMap.newKeySet();
    public ClientChannel register(Consumer<String> sender) {
        ClientChannel channel = new ClientChannel(sender);
        channels.add(channel);
        return channel;
    }

    public void unregister(ClientChannel channel) {
        if (channel != null) channels.remove(channel);
    }

    public void publishToAll(EventType type, long auctionId, long sequence, String payloadJson) {
        String json = serialize(type, auctionId, sequence, payloadJson);
        for (ClientChannel channel : channels) {
            channel.send(json);
        }
    }

    public void publishToUser(long userId, EventType type, long auctionId, long sequence, String payloadJson) {
        String json = serialize(type, auctionId, sequence, payloadJson);
        for (ClientChannel channel : channels) {
            if (channel.userId != null && channel.userId == userId)
                channel.send(json);
        }
    }

    private String serialize(EventType type, long auctionId, long sequence, String payloadJson) {
        EventEnvelope envelope = new EventEnvelope(
                "v1",
                UUID.randomUUID().toString(),
                type,
                auctionId,
                sequence,
                Instant.now(),
                payloadJson
        );
        return JsonCodec.toJson(envelope);
    }

    public static final class ClientChannel {
        private final Consumer<String> sender;
        private volatile Long userId;
        private ClientChannel(Consumer<String> sender) {
            this.sender = sender;
        }
        public void bindUser(Long userId) {
            this.userId = userId;
        }
        public void send(String line) {
            try {
                sender.accept(line);
            } catch (Exception ignored) {
            }
        }
    }
}