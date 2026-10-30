package com.btl.n10.Controller;

import com.btl.n8.DTO.*;
import com.btl.n8.Model.Entity.Auction;
import com.btl.n8.Model.Entity.Bid;
import com.btl.n8.Model.Entity.User;
import com.btl.n8.Model.Enums.AuctionStatus;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Service.BidDetailControllerService;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.btl.n8.Util.UserTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BidDetailController implements ServerResponseListener {

    @FXML private ImageView itemImage;
    @FXML private Label     itemName;
    @FXML private Label     itemId;
    @FXML private Label     itemType;
    @FXML private Label     itemStatus;
    @FXML private Label     currentPrice;
    @FXML private Label     timerLabel;
    @FXML private Label     bidMsg;
    @FXML private TextField bidInput;
    @FXML private LineChart<String, Number> bidChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis   yAxis;
    @FXML private Button autoBidButton;
    @FXML private Button closeBtn;

    private int     auctionId;
    private int     bidderId;
    private Auction auction;
    private String  cachedItemName;
    private Timer   countdownTimer;

    private Stage             autoBidStage;
    private AutoBidController autoBidCtrl;

    /** Service xử lý toàn bộ logic nghiệp vụ — Controller chỉ cập nhật UI */
    private final BidDetailControllerService service = new BidDetailControllerService();

    // FIX: thêm UserTypeAdapter để tránh lỗi "Abstract classes can't be instantiated"
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(User.class, new UserTypeAdapter())
            .create();

    private static final int SNIPE_EXTENSION_SECONDS = 30;

    private static final String STYLE_DEFAULT =
            "-fx-background-color: #0f2035; -fx-text-fill: #ffd700; " +
                    "-fx-border-color: #ffd700; -fx-border-radius: 6; " +
                    "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-width: 1.5;";

    private static final String STYLE_ACTIVE =
            "-fx-background-color: #3a1a1a; -fx-text-fill: #ff6b6b; " +
                    "-fx-border-color: #ff6b6b; -fx-border-radius: 6; " +
                    "-fx-background-radius: 6; -fx-cursor: hand; -fx-border-width: 1.5;";

    // ── Init ─────────────────────────────────────────────────────────────────

    public void initData(int auctionId, Auction auction) {
        this.auctionId = auctionId;
        this.bidderId  = SessionManager.getInstance().getCurrentUser().getId();
        this.auction   = auction;

        // Hiển thị trạng thái ban đầu (placeholder)
        itemName.setText("Auction #" + auctionId);
        itemId.setText("#" + auctionId);
        itemType.setText("-");
        timerLabel.setText("Loading...");
        bidInput.setDisable(true);

        ClientSocket.getInstance().addListener(this);
        AutoBidManager.getInstance().setUICallback(auctionId, this::handleAutoBidManagerEvent);

        if (AutoBidManager.getInstance().isActive(auctionId)) {
            setAutoBidButtonActive();
        }

        // Lấy chi tiết auction + bid history + ảnh qua service
        String sessionId = SessionManager.getInstance().getSessionId();
        service.requestAuctionDetail(sessionId, auctionId);
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject response) {
        if (!response.has("action")) return;
        String action = response.get("action").getAsString();

        switch (action) {
            case "AUCTION_DETAIL_RESULT" -> handleDetailResult(response);
            case "BID_UPDATE"            -> handleBidUpdate(response);
            case "AUCTION_SETTLED"       -> handleSettled(response);
            case "USER_BALANCE_RESULT"   -> handleBalanceResult(response);
        }
    }

    // ── Xử lý response từ server ──────────────────────────────────────────────

    private void handleDetailResult(JsonObject json) {
        GetAuctionDetailResponse res = gson.fromJson(json, GetAuctionDetailResponse.class);
        if (!res.isSuccess() || res.getAuction() == null) {
            Platform.runLater(() -> {
                timerLabel.setText("Error");
                showMsg("Không tải được dữ liệu phiên đấu giá", false);
            });
            return;
        }

        Platform.runLater(() -> {
            this.auction = res.getAuction();
            AutoBidManager.getInstance().updateAuction(auctionId, auction);

            // FIX: hiển thị tên và loại sản phẩm (trước đây luôn là placeholder)
            if (res.getItemName() != null) {
                itemName.setText(res.getItemName());
                cachedItemName = res.getItemName();
            }
            if (res.getItemType() != null) {
                itemType.setText(res.getItemType());
            }

            // FIX: hiển thị ảnh sản phẩm từ Base64 (trước đây ImageView luôn trống)
            loadItemImage(res.getImageBase64());

            updateAuctionStatus();
            loadChartFromBids(res.getBidHistory());

            if (auction != null) {
                if (LocalDateTime.now().isAfter(auction.getEndTime())) {
                    bidInput.setDisable(true);
                    timerLabel.setText("Ended");
                } else {
                    startCountdown(auction.getEndTime());
                }
            }
        });
    }

    /**
     * Decode Base64 và hiển thị ảnh lên ImageView.
     * Nếu imageBase64 là null hoặc rỗng, giữ nguyên placeholder.
     */
    private void loadItemImage(String imageBase64) {
        if (itemImage == null) return;
        if (imageBase64 == null || imageBase64.isEmpty()) {
            System.out.println("[BidDetail] Không có ảnh sản phẩm.");
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(imageBase64);
            Image img = new Image(new ByteArrayInputStream(bytes));
            itemImage.setImage(img);
            itemImage.setPreserveRatio(true);
            itemImage.setSmooth(true);
        } catch (Exception e) {
            System.err.println("[BidDetail] Không load được ảnh: " + e.getMessage());
        }
    }

    private void handleBidUpdate(JsonObject json) {
        BidResponse res = gson.fromJson(json, BidResponse.class);
        if (!service.isCurrentAuction(res.getAuctionId(), this.auctionId)) return;

        Platform.runLater(() -> {
            if (res.isSuccess()) {
                currentPrice.setText(service.formatMoney(res.getCurrentPrice()));
                if (auction != null) {
                    auction.setCurrentPrice(res.getCurrentPrice());
                    AutoBidManager.getInstance().updateAuction(auctionId, auction);
                }
                if (res.getNewEndTime() != null && auction != null) {
                    auction.setEndTime(res.getNewEndTime());
                    AutoBidManager.getInstance().updateAuction(auctionId, auction);
                    if (countdownTimer != null) countdownTimer.cancel();
                    startCountdown(res.getNewEndTime());
                    showMsg("⏰ Anti-snipe! Gia hạn thêm " + SNIPE_EXTENSION_SECONDS + "s", true);
                } else {
                    boolean isMyBid = service.isMyBid(res.getBidderId(), bidderId);
                    showMsg(isMyBid
                                    ? "✓ Your bid was placed!"
                                    : "⚡ Someone placed: " + service.formatMoney(res.getCurrentPrice()),
                            true);
                }
                reloadBidHistory();
            } else {
                if (auction != null && res.getCurrentPrice() != null) {
                    currentPrice.setText(service.formatMoney(res.getCurrentPrice()));
                    auction.setCurrentPrice(res.getCurrentPrice());
                }
                String msg = res.getMessage() != null ? res.getMessage() : "Bid failed";
                showMsg(msg, false);
                if (msg.contains("kết thúc") || msg.contains("đóng") ||
                        msg.contains("ended")    || msg.contains("closed")) {
                    bidInput.setDisable(true);
                    timerLabel.setText("Ended");
                    if (auction != null) {
                        auction.setStatus(AuctionStatus.CLOSED);
                        updateAuctionStatus();
                    }
                }
            }
        });
    }

    private void handleSettled(JsonObject json) {
        AuctionSettledResponse settled = gson.fromJson(json, AuctionSettledResponse.class);
        if (!service.isCurrentAuction(settled.getAuctionId(), this.auctionId)) return;

        Platform.runLater(() -> {
            if (countdownTimer != null) countdownTimer.cancel();
            bidInput.setDisable(true);
            timerLabel.setText("Ended");
            if (auction != null) {
                auction.setStatus(AuctionStatus.CLOSED);
                updateAuctionStatus();
            }

            User me = SessionManager.getInstance().getCurrentUser();

            if (settled.isWinner() && settled.getNewBalance() != null) {
                // [REAL-TIME BALANCE] Buyer thắng → trừ tiền ngay trên giao diện
                if (me != null) {
                    service.applyWinnerBalance(me, settled.getNewBalance());
                    SessionManager.getInstance().setCurrentUser(me);
                }
            } else if (settled.isSeller() && settled.getSellerNewBalance() != null) {
                // [REAL-TIME BALANCE] Seller → cộng tiền ngay trên giao diện
                if (me != null) {
                    me.setBalance(settled.getSellerNewBalance());
                    SessionManager.getInstance().setCurrentUser(me);
                }
            } else {
                String sessionId = SessionManager.getInstance().getSessionId();
                if (me != null) {
                    service.requestUserBalance(sessionId, me.getId());
                }
            }

            // Notify các Controller khác (HomeController, v.v.) cập nhật balance + danh sách
            AppEventBus.publish(AppEventBus.BALANCE_UPDATED, null);
            AppEventBus.publish(AppEventBus.AUCTION_SETTLED, settled.getAuctionId());

            if (settled.getWinnerId() == -1) {
                showSettledDialog("Phiên đấu giá kết thúc",
                        "Không có người tham gia đặt giá.", false);
            } else if (settled.isWinner()) {
                showSettledDialog("🏆 Bạn đã thắng!",
                        String.format("Chúc mừng! Bạn thắng với giá %s.\nBalance đã bị trừ tự động.",
                                service.formatMoney(settled.getWinningPrice())), true);
            } else {
                showSettledDialog("Phiên đấu giá kết thúc",
                        String.format("Người thắng: %s\nGiá thắng: %s",
                                settled.getWinnerAccount(),
                                service.formatMoney(settled.getWinningPrice())), false);
            }
        });
    }

    // ── Balance result ────────────────────────────────────────────────────────

    /**
     * Nhận USER_BALANCE_RESULT từ server sau khi gọi GET_USER_BALANCE.
     * Cập nhật SessionManager với balance mới nhất (sau unfreeze), rồi
     * notify HomeController qua AppEventBus để refresh label balance.
     */
    private void handleBalanceResult(JsonObject json) {
        GetUserBalanceResponse res =
                gson.fromJson(json, GetUserBalanceResponse.class);
        if (!res.isSuccess()) return;

        Platform.runLater(() -> {
            User me = SessionManager.getInstance().getCurrentUser();
            if (me != null && res.getBalance() != null) {
                me.setBalance(res.getBalance());
                me.setFrozenBalance(res.getFrozenBalance());
                SessionManager.getInstance().setCurrentUser(me);
            }
            // Notify HomeController (và bất kỳ ai subscribe) refresh balance
            AppEventBus.publish(AppEventBus.BALANCE_UPDATED, null);
        });
    }

    // ── Reload bid history ────────────────────────────────────────────────────

    private void reloadBidHistory() {
        String sessionId = SessionManager.getInstance().getSessionId();
        service.requestAuctionDetail(sessionId, auctionId);
    }

    // ── Chart ─────────────────────────────────────────────────────────────────

    private void loadChartFromBids(List<Bid> bids) {
        if (bids == null) return;
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Price");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            String label = (i + 1) + " " + bid.getBidTime().format(fmt);
            series.getData().add(new XYChart.Data<>(label, bid.getAmount()));
        }
        bidChart.getData().clear();
        bidChart.getData().add(series);
    }

    // ── Place bid ─────────────────────────────────────────────────────────────

    @FXML
    public void handlePlaceBid() {
        bidMsg.setText("");
        String text = bidInput.getText().trim();
        User me     = SessionManager.getInstance().getCurrentUser();

        BidDetailControllerService.BidValidationResult result =
                service.validateBid(text, auction, me);

        if (!result.valid) {
            showMsg(result.errorMessage, false);
            if (auction != null && (result.errorMessage.contains("ended") ||
                    result.errorMessage.contains("not open"))) {
                auction.setStatus(AuctionStatus.CLOSED);
                updateAuctionStatus();
            }
            return;
        }

        service.sendBidRequest(auctionId, bidderId, result.amount,
                SessionManager.getInstance().getSessionId());
        bidInput.clear();
        showMsg("Bid sent...", true);
    }

    // ── Auction status ────────────────────────────────────────────────────────

    private void updateAuctionStatus() {
        if (auction == null) {
            itemStatus.setText("-");
            currentPrice.setText("-");
            bidInput.setDisable(true);
            return;
        }
        currentPrice.setText(service.formatMoney(auction.getCurrentPrice()));
        itemStatus.setText(auction.getStatus().name());
        switch (auction.getStatus()) {
            case OPEN -> {
                itemStatus.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #0a1628; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 99;");
                bidInput.setDisable(false);
            }
            case CLOSED -> {
                itemStatus.setStyle("-fx-background-color: #888888; -fx-text-fill: white; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 99;");
                bidInput.setDisable(true);
            }
            case CANCELLED -> {
                itemStatus.setStyle("-fx-background-color: #ff6b6b; -fx-text-fill: white; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 99;");
                bidInput.setDisable(true);
            }
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private void startCountdown(LocalDateTime endTime) {
        countdownTimer = new Timer(true);
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long diff = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
                if (diff <= 0) {
                    Platform.runLater(() -> {
                        timerLabel.setText("Ended");
                        bidInput.setDisable(true);
                        if (auction != null) {
                            auction.setStatus(AuctionStatus.CLOSED);
                            updateAuctionStatus();
                        }
                        showMsg("Auction has ended!", false);
                    });
                    countdownTimer.cancel();
                    return;
                }
                long h = diff / 3600, m = (diff % 3600) / 60, s = diff % 60;
                Platform.runLater(() -> timerLabel.setText(
                        String.format("%02d:%02d:%02d", h, m, s)));
            }
        }, 0, 1000);
    }

    // ── AutoBid ───────────────────────────────────────────────────────────────

    private void handleAutoBidManagerEvent(String event) {
        if (event == null) return;
        String[] parts = event.split(":", 2);
        String type = parts[0], msg = parts.length > 1 ? parts[1] : "";
        switch (type) {
            case "CANCELLED" -> { setAutoBidButtonDefault(); showMsg("AutoBid đã huỷ.", false); }
            case "STOPPED"   -> { setAutoBidButtonDefault();
                showMsg("🤖 " + (msg.isEmpty() ? "AutoBid đã dừng." : msg), false); }
        }
    }

    @FXML
    public void handleOpenAutoBid() {
        if (autoBidStage != null && autoBidStage.isShowing()) {
            autoBidStage.requestFocus(); return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AutoBid.fxml"));
            Parent root = loader.load();
            autoBidCtrl = loader.getController();
            autoBidCtrl.initData(auctionId, cachedItemName, auction);
            autoBidCtrl.setOnActiveChanged(active -> Platform.runLater(() -> {
                if (active) setAutoBidButtonActive();
                else        setAutoBidButtonDefault();
            }));
            autoBidStage = new Stage();
            autoBidStage.initStyle(StageStyle.UNDECORATED);
            autoBidStage.setTitle("AutoBid");
            autoBidStage.setScene(new Scene(root));
            autoBidStage.initModality(Modality.NONE);
            autoBidStage.setResizable(false);
            autoBidStage.setOnHidden(e -> {
                if (autoBidCtrl != null) autoBidCtrl.detachUI();
                autoBidCtrl = null; autoBidStage = null;
                AutoBidManager.getInstance().setUICallback(auctionId, this::handleAutoBidManagerEvent);
                if (AutoBidManager.getInstance().isActive(auctionId)) setAutoBidButtonActive();
                else setAutoBidButtonDefault();
            });
            autoBidStage.show();
        } catch (Exception ex) {
            showMsg("Failed to open AutoBid: " + ex.getMessage(), false);
        }
    }

    private void setAutoBidButtonActive() {
        autoBidButton.setText("⚡ ON — Cancel");
        autoBidButton.setStyle(STYLE_ACTIVE);
        autoBidButton.setOnAction(ev -> AutoBidManager.getInstance().cancel(auctionId));
    }

    private void setAutoBidButtonDefault() {
        autoBidButton.setText("⚡ AutoBid");
        autoBidButton.setStyle(STYLE_DEFAULT);
        autoBidButton.setOnAction(ev -> handleOpenAutoBid());
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void cleanup() {
        if (countdownTimer != null) countdownTimer.cancel();
        ClientSocket.getInstance().removeListener(this);
        AutoBidManager.getInstance().setUICallback(auctionId, null);
        if (autoBidCtrl != null) autoBidCtrl.detachUI();
        autoBidCtrl = null; autoBidStage = null;
    }

    @FXML
    public void handleClose() {
        cleanup();
        Stage stage = (Stage) closeBtn.getScene().getWindow();
        stage.close();
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void showMsg(String msg, boolean success) {
        bidMsg.setStyle(success
                ? "-fx-text-fill: #00ff88; -fx-font-size: 12px;"
                : "-fx-text-fill: #ff6b6b; -fx-font-size: 12px;");
        bidMsg.setText(msg);
    }

    private void showSettledDialog(String title, String content, boolean isWinner) {
        Alert alert = new Alert(isWinner
                ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}