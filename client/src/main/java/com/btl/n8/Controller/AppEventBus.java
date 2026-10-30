package com.btl.n8.Controller;

import javafx.application.Platform;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * EventBus nội bộ dùng để giao tiếp giữa các Controller mà không cần
 * coupling trực tiếp. Tất cả subscriber được gọi trên JavaFX Application Thread.
 *
 * Cách dùng:
 *   // Subscribe (thường trong initialize()):
 *   AppEventBus.subscribe(AppEventBus.BALANCE_UPDATED, e -> refreshBalance());
 *
 *   // Publish (từ bất kỳ thread nào):
 *   AppEventBus.publish(AppEventBus.BALANCE_UPDATED, null);
 *
 *   // Unsubscribe khi Controller bị destroy:
 *   AppEventBus.unsubscribe(AppEventBus.BALANCE_UPDATED, myHandler);
 */
public class AppEventBus {

    // ── Event type constants ──────────────────────────────────────────────────

    /** Phát khi balance của user thay đổi (sau settle / deposit / unfreeze). */
    public static final String BALANCE_UPDATED = "BALANCE_UPDATED";

    /** Phát khi một phiên đấu giá kết thúc (AUCTION_SETTLED nhận được). */
    public static final String AUCTION_SETTLED = "AUCTION_SETTLED";

    /** Phát khi danh sách auction cần reload (BID_UPDATE hoặc AUCTION_SETTLED). */
    public static final String AUCTION_LIST_CHANGED = "AUCTION_LIST_CHANGED";

    // ── Internal state ────────────────────────────────────────────────────────

    private static final Map<String, List<Consumer<Object>>> subscribers =
            new ConcurrentHashMap<>();

    private AppEventBus() {}

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Đăng ký lắng nghe một event type.
     * @param eventType  hằng số event (vd: BALANCE_UPDATED)
     * @param handler    callback — luôn được gọi trên JavaFX Application Thread
     */
    public static void subscribe(String eventType, Consumer<Object> handler) {
        subscribers
                .computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    /**
     * Hủy đăng ký. Gọi trong cleanup() của Controller để tránh memory leak.
     */
    public static void unsubscribe(String eventType, Consumer<Object> handler) {
        List<Consumer<Object>> list = subscribers.get(eventType);
        if (list != null) list.remove(handler);
    }

    /**
     * Phát event tới tất cả subscriber đã đăng ký.
     * An toàn khi gọi từ bất kỳ thread nào — dispatch tự động về FX thread.
     * @param eventType  loại event
     * @param payload    dữ liệu đính kèm (có thể null)
     */
    public static void publish(String eventType, Object payload) {
        List<Consumer<Object>> list = subscribers.get(eventType);
        if (list == null || list.isEmpty()) return;

        if (Platform.isFxApplicationThread()) {
            for (Consumer<Object> h : list) {
                try { h.accept(payload); }
                catch (Exception e) {
                    System.err.println("[AppEventBus] Handler error for " + eventType + ": " + e.getMessage());
                }
            }
        } else {
            Platform.runLater(() -> {
                for (Consumer<Object> h : list) {
                    try { h.accept(payload); }
                    catch (Exception e) {
                        System.err.println("[AppEventBus] Handler error for " + eventType + ": " + e.getMessage());
                    }
                }
            });
        }
    }

    /** Xóa toàn bộ subscriber của một event type — dùng khi navigate sang scene mới. */
    public static void clearAll(String eventType) {
        subscribers.remove(eventType);
    }
}