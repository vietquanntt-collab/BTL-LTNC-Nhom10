package com.btl.team10.server.bidding;

import java.time.Duration;
import java.time.Instant;

public record AntiSnipePolicy(
        long windowSeconds,
        long extensionSeconds,
        int maxExtensions) {

    public AntiSnipePolicy {
        if (windowSeconds < 0) {
            throw new IllegalArgumentException("windowSeconds must not be negative");
        }
        if (extensionSeconds < 0) {
            throw new IllegalArgumentException("extensionSeconds must not be negative");
        }
        if (maxExtensions < 0) {
            throw new IllegalArgumentException("maxExtensions must not be negative");
        }
    }

    public static AntiSnipePolicy defaultPolicy() {
        long window = Long.getLong("auction.antisnipe.windowSeconds", 30L);
        long extension = Long.getLong("auction.antisnipe.extensionSeconds", 30L);
        int maxExt = Integer.getInteger("auction.antisnipe.maxExtensions", 5);
        return new AntiSnipePolicy(window, extension, maxExt);
    }

    public boolean shouldExtend(Instant now, Instant endAt, int currentExtensionCount) {
        if (windowSeconds <= 0 || extensionSeconds <= 0 || currentExtensionCount >= maxExtensions) {
            return false;
        }
        long remainingSeconds = Duration.between(now, endAt).getSeconds();
        return remainingSeconds >= 0 && remainingSeconds <= windowSeconds;
    }
}