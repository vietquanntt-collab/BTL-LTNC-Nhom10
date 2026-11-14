package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.EventEnvelope;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AuctionDetailController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Label auctionTitleLabel;
    @FXML private Label statusBadgeLabel;
    @FXML private Label alertBannerLabel;
    @FXML private Label itemNameLabel;
    @FXML private Label itemDescLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label minIncrementLabel;
    @FXML private Label minNextBidLabel;
    @FXML private Label countdownLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button quickFillButton;
    @FXML private Button placeBidButton;

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private TableView<BidDto> bidHistoryTable;
    @FXML private TableColumn<BidDto, Long> bidderCol;
    @FXML private TableColumn<BidDto, Long> amountCol;
    @FXML private TableColumn<BidDto, String> timeCol;
    @FXML private TableColumn<BidDto, String> statusCol;

    private final ObservableList<BidDto> bidRows = FXCollections.observableArrayList();
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private final AtomicBoolean submittingBid = new AtomicBoolean(false);
    private final Consumer<EventEnvelope> realtimeListener = this::onServerEvent;

    private Timeline countdownTimeline;
    private AuctionDto currentAuction;
    private volatile long lastSequence = 0L;

    @FXML
    private void initialize() {
        // Dùng lambda accessor để tương thích an toàn với Java 21 record (BidDto)
        bidderCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().bidderId()));
        amountCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().amountCents()));
        timeCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(TIME_FMT.format(c.getValue().bidTime())));
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().name()));

        bidHistoryTable.setItems(bidRows);
        bidHistoryChart.getData().add(priceSeries);

        NetworkClient.getInstance().addEventListener(realtimeListener);
        NetworkClient.getInstance().setConnectionStateListener(connected -> Platform.runLater(() -> {
            if (!connected) {
                showBanner("Mất kết nối tới server! Đang tự động kết nối lại...", "#f39c12");
                setBidControlsEnabled(false);
            } else {
                showBanner("Đã khôi phục kết nối. Đang đồng bộ dữ liệu mới nhất...", "#27ae60");
                if (currentAuction != null) {
                    refreshDetailFromServer(currentAuction.auctionId());
                }
            }
        }));
    }

    public void initAuction(AuctionDto initialAuction) {
        this.currentAuction = initialAuction;
        applyAuctionState(initialAuction, null);
        startCountdown();
        refreshDetailFromServer(initialAuction.auctionId());
    }

    private void refreshDetailFromServer(long auctionId) {
        Thread loader = new Thread(() -> {
            try {
                NetworkClient.AuctionDetailData detail = NetworkClient.getInstance().getAuctionDetail(auctionId);
                Platform.runLater(() -> {
                    this.currentAuction = detail.auction();
                    applyAuctionState(detail.auction(), detail.item());
                    updateBidHistory(detail.bids());
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                        showBanner("Không thể tải chi tiết mới nhất: " + e.getMessage(), "#e74c3c"));
            }
        }, "auction-detail-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void applyAuctionState(AuctionDto auction, ItemDto item) {
        auctionTitleLabel.setText("Phiên đấu giá #" + auction.auctionId());
        if (item != null) {
            itemNameLabel.setText(item.name() + " [" + item.itemType() + "]");
            itemDescLabel.setText(item.description().isBlank() ? "Không có mô tả." : item.description());
        } else {
            itemNameLabel.setText("Vật phẩm #" + auction.itemId());
        }

        currentPriceLabel.setText(auction.currentPriceCents() + " cents");
        minIncrementLabel.setText("+" + auction.minIncrementCents() + " cents");
        long minValid = computeMinValidBid(auction);
        minNextBidLabel.setText(minValid + " cents");

        statusBadgeLabel.setText(auction.status().name());
        boolean isClosed = isAuctionEnded(auction);
        if (isClosed) {
            statusBadgeLabel.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            setBidControlsEnabled(false);
        } else {
            statusBadgeLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            if (!submittingBid.get()) {
                setBidControlsEnabled(true);
            }
        }
    }

    private long computeMinValidBid(AuctionDto auction) {
        // Nếu chưa có lượt đặt nào (giá hiện tại bằng giá khởi điểm và chưa có bid), có thể đặt từ startPrice hoặc current + increment
        return bidRows.isEmpty() && auction.currentPriceCents() == auction.startPriceCents()
                ? auction.startPriceCents()
                : auction.currentPriceCents() + auction.minIncrementCents();
    }

    private boolean isAuctionEnded(AuctionDto auction) {
        return auction.status() == AuctionStatus.FINISHED
                || auction.status() == AuctionStatus.PAID
                || auction.status() == AuctionStatus.CANCELLED
                || !Instant.now().isBefore(auction.endAt());
    }

    @FXML
    private void handleFillMinBid() {
        if (currentAuction != null) {
            bidAmountField.setText(String.valueOf(computeMinValidBid(currentAuction)));
        }
    }

    @FXML
    private void handlePlaceBid() {
        if (currentAuction == null) {
            return;
        }
        if (isAuctionEnded(currentAuction)) {
            showBanner("Phiên đấu giá đã kết thúc, không thể đặt giá.", "#e74c3c");
            return;
        }

        long amountCents;
        try {
            amountCents = Long.parseLong(bidAmountField.getText().trim());
        } catch (NumberFormatException e) {
            showBanner("Vui lòng nhập số tiền hợp lệ (số nguyên cents).", "#e74c3c");
            return;
        }

        long minValid = computeMinValidBid(currentAuction);
        if (amountCents < minValid) {
            showBanner("Mức giá tối thiểu hợp lệ hiện tại là " + minValid + " cents.", "#e74c3c");
            return;
        }

        // Chống double-submit: khóa nút ngay lập tức và tạo 1 requestId duy nhất cho lần bấm này
        if (!submittingBid.compareAndSet(false, true)) {
            return;
        }
        placeBidButton.setDisable(true);
        placeBidButton.setText("Đang gửi...");
        String clientRequestId = UUID.randomUUID().toString();
        long auctionId = currentAuction.auctionId();

        Thread bidThread = new Thread(() -> {
            try {
                NetworkClient.PlaceBidResult result =
                        NetworkClient.getInstance().placeBid(auctionId, amountCents, clientRequestId);
                Platform.runLater(() -> {
                    submittingBid.set(false);
                    placeBidButton.setText("Đặt giá ngay");
                    bidAmountField.clear();
                    showBanner("Đặt giá thành công: " + result.amountCents() + " cents!", "#27ae60");
                    updatePriceLocally(result.currentPriceCents(), result.auctionVersion());
                    if (!isAuctionEnded(currentAuction)) {
                        placeBidButton.setDisable(false);
                    }
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    submittingBid.set(false);
                    placeBidButton.setText("Đặt giá ngay");
                    showBanner("Đặt giá thất bại: " + error.getMessage(), "#e74c3c");
                    if (!isAuctionEnded(currentAuction)) {
                        placeBidButton.setDisable(false);
                    }
                });
            }
        }, "auction-place-bid");
        bidThread.setDaemon(true);
        bidThread.start();
    }

    private void onServerEvent(EventEnvelope event) {
        if (currentAuction == null || event.auctionId() != currentAuction.auctionId()) {
            return;
        }
        // Bỏ qua sự kiện cũ hoặc trùng lặp theo protocol v1
        if (event.sequence() <= lastSequence) {
            return;
        }
        lastSequence = event.sequence();

        Platform.runLater(() -> {
            JsonObject payload = parsePayload(event.payloadJson());
            switch (event.eventType()) {
                case BID_ACCEPTED -> {
                    long newPrice = payload.has("currentPriceCents")
                            ? payload.get("currentPriceCents").getAsLong()
                            : (payload.has("amountCents") ? payload.get("amountCents").getAsLong() : currentAuction.currentPriceCents());
                    long version = payload.has("auctionVersion") ? payload.get("auctionVersion").getAsLong() : currentAuction.version() + 1;
                    long bidderId = payload.has("bidderId") ? payload.get("bidderId").getAsLong() : 0L;
                    long bidId = payload.has("bidId") ? payload.get("bidId").getAsLong() : event.sequence();

                    updatePriceLocally(newPrice, version);
                    if (bidderId > 0) {
                        appendBidPoint(new BidDto(bidId, currentAuction.auctionId(), bidderId, newPrice,
                                event.occurredAt(), BidStatus.ACTIVE, event.eventId()));
                    }
                    if (bidderId != NetworkClient.getInstance().userId()) {
                        showBanner("Có lượt đặt giá mới: " + newPrice + " cents!", "#2980b9");
                    }
                }
                case OUTBID -> {
                    long newPrice = payload.has("currentPriceCents")
                            ? payload.get("currentPriceCents").getAsLong()
                            : currentAuction.currentPriceCents();
                    updatePriceLocally(newPrice, currentAuction.version() + 1);
                    showBanner("CẢNH BÁO: Bạn đã bị vượt giá (OUTBID)! Giá hiện tại là " + newPrice + " cents.", "#e67e22");
                }
                case BID_REJECTED -> {
                    String reason = payload.has("reason") ? payload.get("reason").getAsString() : "Lệnh đặt giá bị từ chối.";
                    showBanner("Lệnh đặt giá bị từ chối: " + reason, "#e74c3c");
                }
                case AUCTION_EXTENDED -> {
                    if (payload.has("endAt")) {
                        Instant newEndAt = Instant.parse(payload.get("endAt").getAsString());
                        currentAuction = new AuctionDto(
                                currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                                currentAuction.currentPriceCents(), currentAuction.minIncrementCents(),
                                currentAuction.startAt(), newEndAt, currentAuction.status(),
                                currentAuction.version() + 1, currentAuction.extensionCount() + 1,
                                currentAuction.winnerId(), currentAuction.closedAt(), currentAuction.createdAt());
                    }
                    showBanner("Anti-Snipe: Phiên đấu giá vừa được gia hạn thêm thời gian!", "#8e44ad");
                }
                case AUCTION_FINISHED, AUCTION_SETTLED -> {
                    Long winnerId = payload.has("winnerId") && !payload.get("winnerId").isJsonNull()
                            ? payload.get("winnerId").getAsLong() : currentAuction.winnerId();
                    AuctionStatus nextStatus = event.eventType() == com.btl.team10.common.protocol.EventType.AUCTION_SETTLED
                            ? AuctionStatus.PAID : AuctionStatus.FINISHED;
                    currentAuction = new AuctionDto(
                            currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                            currentAuction.currentPriceCents(), currentAuction.minIncrementCents(),
                            currentAuction.startAt(), currentAuction.endAt(), nextStatus,
                            currentAuction.version() + 1, currentAuction.extensionCount(),
                            winnerId, event.occurredAt(), currentAuction.createdAt());
                    applyAuctionState(currentAuction, null);
                    String winnerText = (winnerId != null) ? " Người thắng: Bidder #" + winnerId : " Không có người thắng.";
                    showBanner("Phiên đấu giá đã kết thúc!" + winnerText, "#2c3e50");
                }
                case ERROR -> {
                    String msg = payload.has("message") ? payload.get("message").getAsString() : "Lỗi từ server.";
                    showBanner(msg, "#e74c3c");
                }
                default -> {
                }
            }
        });
    }

    private void updatePriceLocally(long newPriceCents, long newVersion) {
        if (currentAuction == null) {
            return;
        }
        currentAuction = new AuctionDto(
                currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                Math.max(currentAuction.currentPriceCents(), newPriceCents), currentAuction.minIncrementCents(),
                currentAuction.startAt(), currentAuction.endAt(), currentAuction.status(),
                Math.max(currentAuction.version(), newVersion), currentAuction.extensionCount(),
                currentAuction.winnerId(), currentAuction.closedAt(), currentAuction.createdAt());
        applyAuctionState(currentAuction, null);
    }

    private void updateBidHistory(List<BidDto> bids) {
        List<BidDto> sorted = new ArrayList<>(bids);
        sorted.sort(Comparator.comparing(BidDto::bidTime).thenComparingLong(BidDto::bidId));

        bidRows.setAll(sorted);
        priceSeries.getData().clear();
        for (BidDto bid : sorted) {
            priceSeries.getData().add(new XYChart.Data<>(TIME_FMT.format(bid.bidTime()), bid.amountCents()));
        }
        if (currentAuction != null) {
            minNextBidLabel.setText(computeMinValidBid(currentAuction) + " cents");
        }
    }

    private void appendBidPoint(BidDto bid) {
        boolean exists = bidRows.stream().anyMatch(b -> b.bidId() == bid.bidId());
        if (exists) {
            return;
        }
        bidRows.add(bid);
        priceSeries.getData().add(new XYChart.Data<>(TIME_FMT.format(bid.bidTime()), bid.amountCents()));
    }

    private void startCountdown() {
        stopCountdown();
        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateCountdownTick()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        updateCountdownTick();
        countdownTimeline.play();
    }

    private void updateCountdownTick() {
        if (currentAuction == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), currentAuction.endAt());
        if (remaining.isNegative() || remaining.isZero() || isAuctionEnded(currentAuction)) {
            countdownLabel.setText("00:00:00 (Đã đóng)");
            countdownLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
            setBidControlsEnabled(false);
            stopCountdown();
            return;
        }
        long totalSeconds = remaining.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        countdownLabel.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        if (totalSeconds <= 60) {
            countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        } else {
            countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2980b9;");
        }
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void setBidControlsEnabled(boolean enabled) {
        bidAmountField.setDisable(!enabled);
        quickFillButton.setDisable(!enabled);
        placeBidButton.setDisable(!enabled);
    }

    private void showBanner(String message, String hexColor) {
        alertBannerLabel.setText(message);
        alertBannerLabel.setStyle("-fx-background-color: " + hexColor
                + "; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        alertBannerLabel.setVisible(true);
        alertBannerLabel.setManaged(true);
    }

    private JsonObject parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(payloadJson).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    @FXML
    private void handleBack() {
        stopCountdown();
        NetworkClient.getInstance().removeEventListener(realtimeListener);
        NetworkClient.getInstance().setConnectionStateListener(null);
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
    }
}