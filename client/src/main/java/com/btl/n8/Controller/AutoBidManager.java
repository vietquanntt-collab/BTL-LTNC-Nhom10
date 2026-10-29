package com.btl.n8.Controller;

import com.btl.n8.Model.Entity.Auction;
import com.btl.n8.Model.Enums.AuctionStatus;
import com.btl.n8.DTO.BidRequest;
import com.btl.n8.DTO.BidResponse;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Singleton quản lý AutoBid, sống độc lập với UI.
 * Bid Sniping đã bị loại bỏ — chỉ còn AutoBid thông thường:
 * tự động outbid khi bị người khác vượt giá, miễn là nextBid <= maxPrice.
 */
public class AutoBidManager implements ServerResponseListener {

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static volatile AutoBidManager instance;

    public static AutoBidManager getInstance() {
        if (instance == null) {
            synchronized (AutoBidManager.class) {
                if (instance == null) instance = new AutoBidManager();
            }
        }
        return instance;
    }

    private AutoBidManager() {}

    // ── Session data per auction ──────────────────────────────────────────────
    public static class AutoBidSession {
        public final int        auctionId;
        public final int        bidderId;
        public final BigDecimal maxPrice;
        public final BigDecimal step;
        public       Auction    auction;
        public final AtomicBoolean active  = new AtomicBoolean(true);
        /**
         * true = mình đang là highest bidder (vừa bid thành công, chưa bị ai vượt).
         * Khi isLeading=true, AutoBid KHÔNG tự bid lại — chỉ bid khi bị người khác vượt.
         * Khởi tạo = false để lần đầu activate sẽ bid nếu chưa dẫn đầu.
         */
        public volatile boolean isLeading = false;

        public AutoBidSession(int auctionId, int bidderId,
                              BigDecimal maxPrice, BigDecimal step,
                              Auction auction) {
            this.auctionId = auctionId;
            this.bidderId  = bidderId;
            this.maxPrice  = maxPrice;
            this.step      = step;
            this.auction   = auction;
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private final Map<Integer, AutoBidSession>  sessions    = new ConcurrentHashMap<>();
    private final Map<Integer, Consumer<String>> uiCallbacks = new ConcurrentHashMap<>();
    private final AtomicBoolean listenerRegistered = new AtomicBoolean(false);

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isActive(int auctionId) {
        AutoBidSession s = sessions.get(auctionId);
        return s != null && s.active.get();
    }

    public AutoBidSession getSession(int auctionId) {
        return sessions.get(auctionId);
    }

    /** Kích hoạt AutoBid. Gọi từ AutoBidController khi nhấn Activate. */
    public void activate(int auctionId, int bidderId,
                         BigDecimal maxPrice, BigDecimal step,
                         Auction auction) {
        cancelSilent(auctionId);

        AutoBidSession session = new AutoBidSession(
                auctionId, bidderId, maxPrice, step, auction);
        sessions.put(auctionId, session);

        if (listenerRegistered.compareAndSet(false, true)) {
            ClientSocket.getInstance().addListener(this);
        }

        // Khi activate, chỉ bid ngay nếu currentPrice + step <= maxPrice.
        // Nếu không thể bid cao hơn nữa thì dừng luôn, không cần gửi lên server.
        if (auction != null) {
            BigDecimal nextBid = auction.getCurrentPrice().add(step);
            if (nextBid.compareTo(maxPrice) > 0 && maxPrice.compareTo(auction.getCurrentPrice()) <= 0) {
                // maxPrice không vượt được currentPrice → AutoBid vô nghĩa
                notifyUI(auctionId, "STOPPED:Max price thấp hơn giá hiện tại – AutoBid không thể chạy.");
                cancelSilent(auctionId);
                return;
            }
        }
        checkAndBid(session);
    }

    /** Huỷ AutoBid và thông báo UI. Chỉ gọi khi người dùng nhấn Cancel. */
    public void cancel(int auctionId) {
        cancelSilent(auctionId);
        notifyUI(auctionId, "CANCELLED");
    }

    /** Huỷ tất cả — gọi khi logout. */
    public void cancelAll() {
        for (int id : sessions.keySet()) {
            cancelSilent(id);
            notifyUI(id, "CANCELLED");
        }
        if (listenerRegistered.compareAndSet(true, false)) {
            ClientSocket.getInstance().removeListener(this);
        }
        uiCallbacks.clear();
    }

    /** Cập nhật auction reference khi có giá/endTime mới. */
    public void updateAuction(int auctionId, Auction auction) {
        AutoBidSession s = sessions.get(auctionId);
        if (s != null) s.auction = auction;
    }

    public void setUICallback(int auctionId, Consumer<String> callback) {
        if (callback == null) uiCallbacks.remove(auctionId);
        else                  uiCallbacks.put(auctionId, callback);
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject response) {
        if (!response.has("action")) return;
        if (!"BID_UPDATE".equals(response.get("action").getAsString())) return;

        BidResponse res = gson.fromJson(response, BidResponse.class);
        if (!res.isSuccess()) {
            // Server từ chối bid → reset isLeading về false.
            // QUAN TRỌNG: khi reject, server trả bidderId = -1 (không phải id thật)
            // → không thể dùng bidderId để filter, phải dùng auctionId.
            AutoBidSession s = sessions.get(res.getAuctionId());
            if (s != null) {
                s.isLeading = false;
                // Cập nhật currentPrice từ response (server luôn gửi kèm giá hiện tại)
                if (s.auction != null && res.getCurrentPrice() != null) {
                    s.auction.setCurrentPrice(res.getCurrentPrice());
                }
                // KHÔNG retry tự động khi bị reject — tránh spam server.
                // AutoBid sẽ phản ứng lại khi CÓ người khác bid (BID_UPDATE success).
            }
            return;
        }

        int auctionId = res.getAuctionId();
        AutoBidSession session = sessions.get(auctionId);
        if (session == null || !session.active.get()) return;

        if (session.auction != null) {
            session.auction.setCurrentPrice(res.getCurrentPrice());
            // Cập nhật endTime nếu server gia hạn (Anti-sniping)
            if (res.getNewEndTime() != null) {
                session.auction.setEndTime(res.getNewEndTime());
            }
        }

        if (res.getBidderId() == session.bidderId) {
            // Chính mình vừa bid thành công → đánh dấu đang dẫn đầu, KHÔNG bid lại.
            session.isLeading = true;
        } else {
            // Người khác vừa bid → mình bị vượt giá → cần phản ứng.
            session.isLeading = false;
            Platform.runLater(() -> checkAndBid(session));
        }
    }

    // ── AutoBid logic ─────────────────────────────────────────────────────────

    private void checkAndBid(AutoBidSession session) {
        if (!session.active.get() || session.auction == null) return;

        // Guard: nếu đang dẫn đầu rồi thì không cần bid thêm.
        // isLeading sẽ bị reset về false khi người khác vượt giá (trong onRespone).
        if (session.isLeading) return;

        if (session.auction.getStatus() != AuctionStatus.OPEN) {
            cancelSilent(session.auctionId);
            notifyUI(session.auctionId, "STOPPED:Auction closed – AutoBid stopped.");
            return;
        }

        BigDecimal currentPrice = session.auction.getCurrentPrice();
        BigDecimal nextBid = currentPrice.add(session.step);

        // Cap nextBid tại maxPrice thay vì dừng ngay:
        // Nếu nextBid vượt maxPrice nhưng maxPrice vẫn > currentPrice
        // → thử bid đúng bằng maxPrice (bid hợp lệ cuối cùng)
        if (nextBid.compareTo(session.maxPrice) > 0) {
            if (session.maxPrice.compareTo(currentPrice) > 0) {
                nextBid = session.maxPrice; // bid bằng đúng maxPrice
            } else {
                // maxPrice cũng không vượt được currentPrice → thực sự hết
                cancelSilent(session.auctionId);
                notifyUI(session.auctionId, "STOPPED:Đã đạt giá tối đa – AutoBid dừng.");
                return;
            }
        }

        // Kiểm tra balance: nextBid không được vượt balance hiện tại
        com.btl.n8.Model.Entity.User currentUser = SessionManager.getInstance().getCurrentUser();
        BigDecimal balance = currentUser != null && currentUser.getAvailableBalance() != null
                ? currentUser.getAvailableBalance() : BigDecimal.ZERO;
        if (nextBid.compareTo(balance) > 0) {
            cancelSilent(session.auctionId);
            notifyUI(session.auctionId, "STOPPED:Số dư không đủ để tiếp tục AutoBid – Đã dừng.");
            return;
        }

        final BigDecimal bidAmount = nextBid;
        // Đánh dấu đang chờ bid confirm — ngăn checkAndBid gửi thêm bid trùng lặp
        // trong khoảng thời gian server đang xử lý. isLeading sẽ được confirm lại
        // khi server trả về BID_UPDATE với bidderId = mình.
        session.isLeading = true;
        new Thread(() -> {
            // Delay nhỏ để chắc server đã commit bid trước trước khi gửi bid tiếp
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (!session.active.get()) return; // check lại sau delay
            BidRequest req = new BidRequest(session.auctionId, session.bidderId, bidAmount);
            req.setSessionId(SessionManager.getInstance().getSessionId());
            ClientSocket.getInstance().sendMessage(req);
            notifyUI(session.auctionId, "BID_PLACED:" + bidAmount.toPlainString());
        }).start();
    }
    // ── Internal ──────────────────────────────────────────────────────────────
    private void cancelSilent(int auctionId) {
        AutoBidSession s = sessions.remove(auctionId);
        if (s != null) s.active.set(false);
        if (sessions.isEmpty() && listenerRegistered.compareAndSet(true, false)) {
            ClientSocket.getInstance().removeListener(this);
        }
    }

    private void notifyUI(int auctionId, String event) {
        Consumer<String> cb = uiCallbacks.get(auctionId);
        if (cb != null) Platform.runLater(() -> cb.accept(event));
    }
}