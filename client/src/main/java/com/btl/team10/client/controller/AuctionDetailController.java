package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.client.util.UiMotion;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
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
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

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
    @FXML private Label extensionCountLabel;
    @FXML private Label statusBadgeLabel;
    @FXML private Label alertBannerLabel;

    @FXML private HBox winnerBannerBox;
    @FXML private Label winnerTitleLabel;
    @FXML private Label settlementStatusLabel;

    @FXML private StackPane galaStageRoot;
    @FXML private ImageView stageBackdropView;
    @FXML private Region stageAtmosphereOverlay;
    @FXML private StackPane itemFloatingBox;
    @FXML private ImageView itemStageImageView;
    @FXML private VBox defaultArtifactBox;
    @FXML private Label defaultArtifactIconLabel;
    @FXML private ImageView pedestalImageView;
    @FXML private VBox fallbackPedestalBox;
    @FXML private VBox mcSpeechBubble;
    @FXML private Label mcMoodLabel;
    @FXML private Label mcDialogueLabel;
    @FXML private StackPane mcActorBox;
    @FXML private ImageView mcImageView;

    @FXML private Label itemNameLabel;
    @FXML private Label itemDescLabel;
    @FXML private Label leadStatusLabel;
    @FXML private Label currentPriceLabel;
    @FXML private Label minIncrementLabel;
    @FXML private Label minNextBidLabel;

    @FXML private Label countdownLabel;
    @FXML private TextField bidAmountField;
    @FXML private Button quickFillButton;
    @FXML private Button placeBidButton;

    @FXML private Label autoBidStatusLabel;
    @FXML private TextField autoBidMaxField;
    @FXML private TextField autoBidIncrementField;
    @FXML private Button setAutoBidButton;
    @FXML private Button cancelAutoBidButton;

    @FXML private LineChart<String, Number> bidHistoryChart;
    @FXML private TableView<BidDto> bidHistoryTable;
    @FXML private TableColumn<BidDto, Long> bidderCol;
    @FXML private TableColumn<BidDto, Long> amountCol;
    @FXML private TableColumn<BidDto, String> timeCol;
    @FXML private TableColumn<BidDto, String> statusCol;

    private final ObservableList<BidDto> bidRows = FXCollections.observableArrayList();
    private final XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();
    private final AtomicBoolean submittingBid = new AtomicBoolean(false);
    private final AtomicBoolean submittingAutoBid = new AtomicBoolean(false);
    private final Consumer<EventEnvelope> realtimeListener = this::onServerEvent;

    private Timeline countdownTimeline;
    private Timeline mcTypingTimeline;
    private Timeline priceCounterTimeline;
    private TranslateTransition stageFloatAnimation;
    private AuctionDto currentAuction;
    private ItemDto currentItem;
    private Image mcPresentSprite;
    private Image mcHypeSprite;
    private long displayedPriceCents = -1L;
    private boolean confettiCelebrated = false;
    private volatile long lastSequence = 0L;
    private boolean disposed;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    @FXML
    private void initialize() {
        bidderCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().bidderId()));
        amountCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().amountCents()));
        timeCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(TIME_FMT.format(c.getValue().bidTime())));
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().status() == BidStatus.ACTIVE ? "DẪN ĐẦU" : c.getValue().status().name()));

        bidHistoryTable.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BidDto item, boolean empty) {
                super.updateItem(item, empty);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("leading"),
                        !empty && item != null && item.status() == BidStatus.ACTIVE);
            }
        });

        bidHistoryTable.setPlaceholder(new Label("Chưa có lượt đặt giá nào trên sàn."));
        bidHistoryTable.setItems(bidRows);
        bidHistoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        bidHistoryChart.getData().add(priceSeries);

        UiMotion.attachHoverLift(placeBidButton);
        UiMotion.attachHoverLift(setAutoBidButton);
        setupStageAssets();
        startStageFloatAnimation();

        NetworkClient.getInstance().addEventListener(realtimeListener);
        NetworkClient.getInstance().setConnectionStateListener(connected -> Platform.runLater(() -> {
            if (disposed) return;
            if (!connected) {
                showBanner("Mất kết nối tới server! Đang tự động kết nối lại...", "#f39c12");
                updateMcSpeech("MẤT TÍN HIỆU",
                        "Tín hiệu trường quay đang gián đoạn, hệ thống đang nối lại đường truyền...",
                        "#f39c12", false);
                setBidControlsEnabled(false);
            } else {
                showBanner("Đã khôi phục kết nối. Đang đồng bộ dữ liệu mới nhất...", "#27ae60");
                updateMcSpeech("TRỞ LẠI SÀN",
                        "Đường truyền đã thông suốt! Mời các nhà sưu tầm tiếp tục trả giá!",
                        "#27ae60", true);
                if (currentAuction != null) {
                    refreshDetailFromServer(currentAuction.auctionId());
                }
            }
        }));
    }

    private void setupStageAssets() {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        clip.widthProperty().bind(galaStageRoot.widthProperty());
        clip.heightProperty().bind(galaStageRoot.heightProperty());
        galaStageRoot.setClip(clip);

        Image backdrop = StageAssetLoader.loadRawBackdrop("stage_bg");
        if (backdrop != null) {
            stageBackdropView.setImage(backdrop);
            stageBackdropView.fitWidthProperty().bind(galaStageRoot.widthProperty());
            stageBackdropView.fitHeightProperty().bind(galaStageRoot.heightProperty());
        }

        Image pedestal = StageAssetLoader.loadCutoutSprite("pedestal");
        if (pedestal != null) {
            pedestalImageView.setImage(pedestal);
            pedestalImageView.setVisible(true);
            fallbackPedestalBox.setVisible(false);
            fallbackPedestalBox.setManaged(false);
        } else {
            pedestalImageView.setVisible(false);
            fallbackPedestalBox.setVisible(true);
            fallbackPedestalBox.setManaged(true);
        }

        mcPresentSprite = StageAssetLoader.loadCutoutSprite("mc_present");
        mcHypeSprite = StageAssetLoader.loadCutoutSprite("mc_hype");
        if (mcPresentSprite == null) {
            mcPresentSprite = mcHypeSprite;
        }
        if (mcHypeSprite == null) {
            mcHypeSprite = mcPresentSprite;
        }
        if (mcPresentSprite != null) {
            mcImageView.setImage(mcPresentSprite);
        }
    }

    public void initAuction(AuctionDto initialAuction) {
        this.currentAuction = initialAuction;
        if (autoBidIncrementField.getText().isBlank()) {
            autoBidIncrementField.setText(String.valueOf(initialAuction.minIncrementCents()));
        }
        applyAuctionState(initialAuction, null);
        startCountdown();
        refreshDetailFromServer(initialAuction.auctionId());
    }

    private void refreshDetailFromServer(long auctionId) {
        if (disposed || !refreshing.compareAndSet(false, true)) return;
        Thread loader = new Thread(() -> {
            try {
                NetworkClient.AuctionDetailData detail = NetworkClient.getInstance().getAuctionDetail(auctionId);
                Platform.runLater(() -> {
                    if (disposed) return;
                    if (currentAuction != null && detail.auction().version() < currentAuction.version()) return;
                    if (currentAuction != null && currentAuction.status() == AuctionStatus.PAID
                            && detail.auction().status() != AuctionStatus.PAID) return;
                    if (currentAuction != null && currentAuction.status() == AuctionStatus.CANCELLED
                            && detail.auction().status() != AuctionStatus.CANCELLED) return;
                    this.currentAuction = detail.auction();
                    this.currentItem = detail.item();
                    updateBidHistory(detail.bids());
                    applyAuctionState(detail.auction(), detail.item());
                    if (!isAuctionEnded(detail.auction())) startCountdown();
                });
            } catch (Exception e) {
                Platform.runLater(() ->
                        { if (!disposed) showBanner("Không thể tải chi tiết mới nhất: " + e.getMessage(), "#e74c3c"); });
            } finally {
                refreshing.set(false);
            }
        }, "auction-detail-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void applyAuctionState(AuctionDto auction, ItemDto item) {
        auctionTitleLabel.setText("Phiên đấu giá #" + auction.auctionId());
        extensionCountLabel.setText("Anti-Snipe: " + auction.extensionCount() + " lần gia hạn");

        if (item != null) {
            this.currentItem = item;
            itemNameLabel.setText(item.name() + " [" + item.itemType() + "]");
            String desc = item.description().isBlank() ? "Siêu phẩm sưu tầm độc bản trên sân khấu Gala." : item.description();
            itemDescLabel.setText(desc);
            renderStageArtifact(item);

            if (!isAuctionEnded(auction)) {
                updateMcSpeech("GIỚI THIỆU VẬT PHẨM",
                        "Xin mời quý vị chiêm ngưỡng \"" + item.name() + "\" (" + item.itemType() + ")! "
                                + desc + " — Giá sàn hiện tại là " + auction.currentPriceCents() + " cents!",
                        "#f1c40f", false);
            }
        } else if (itemNameLabel.getText().startsWith("Đang tải")) {
            itemNameLabel.setText("Vật phẩm #" + auction.itemId());
        }

        long targetPrice = auction.currentPriceCents();
        if (displayedPriceCents < 0) {
            displayedPriceCents = targetPrice;
            currentPriceLabel.setText(money(targetPrice));
        } else if (targetPrice != displayedPriceCents) {
            priceCounterTimeline = UiMotion.animatePriceCounter(
                    currentPriceLabel, displayedPriceCents, targetPrice, " cents", priceCounterTimeline);
            displayedPriceCents = targetPrice;
        }

        minIncrementLabel.setText("+" + money(auction.minIncrementCents()));
        long minValid = computeMinValidBid(auction);
        minNextBidLabel.setText(money(minValid));

        statusBadgeLabel.setText(auction.status().name());
        boolean isClosed = isAuctionEnded(auction);
        if (isClosed) {
            String badgeColor = auction.status() == AuctionStatus.PAID ? "#2980b9" : "#7f8c8d";
            statusBadgeLabel.setStyle("-fx-background-color: " + badgeColor
                    + "; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            setBidControlsEnabled(false);
            updateWinnerBanner(auction);
            announceClosedByMc(auction);
            if (!confettiCelebrated && auction.winnerId() != null && auction.status() != AuctionStatus.CANCELLED) {
                confettiCelebrated = true;
                UiMotion.burstConfetti(galaStageRoot);
            }
        } else {
            statusBadgeLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4; -fx-font-weight: bold;");
            winnerBannerBox.setVisible(false);
            winnerBannerBox.setManaged(false);
            if (!submittingBid.get() && !submittingAutoBid.get()) {
                setBidControlsEnabled(true);
            }
        }
        updateLeadStatusBadge();
    }

    private void renderStageArtifact(ItemDto item) {
        defaultArtifactIconLabel.setText(item.itemType() != null ? item.itemType().name() : "VẬT PHẨM");
        Image stageSprite = StageAssetLoader.resolveItemStageImage(item);
        if (stageSprite != null) {
            itemStageImageView.setImage(stageSprite);
            itemStageImageView.setVisible(true);
            defaultArtifactBox.setVisible(false);
            defaultArtifactBox.setManaged(false);
        } else {
            itemStageImageView.setImage(null);
            itemStageImageView.setVisible(false);
            defaultArtifactBox.setVisible(true);
            defaultArtifactBox.setManaged(true);
        }
    }

    private void startStageFloatAnimation() {
        stopStageFloatAnimation();
        stageFloatAnimation = new TranslateTransition(javafx.util.Duration.millis(1450), itemFloatingBox);
        stageFloatAnimation.setFromY(2);
        stageFloatAnimation.setToY(-8);
        stageFloatAnimation.setAutoReverse(true);
        stageFloatAnimation.setCycleCount(Animation.INDEFINITE);
        stageFloatAnimation.play();
    }

    private void stopStageFloatAnimation() {
        if (stageFloatAnimation != null) {
            stageFloatAnimation.stop();
            stageFloatAnimation = null;
        }
    }

    private void playStageHighlight() {
        UiMotion.pulse(itemFloatingBox, 1.11);
        UiMotion.pulse(mcActorBox, 1.05);
    }

    private void updateMcSpeech(String mood, String line, String accentHex, boolean excitedPose) {
        mcMoodLabel.setText(mood);
        mcMoodLabel.setStyle("-fx-text-fill: " + accentHex + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        mcDialogueLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 13px; -fx-font-weight: bold;");
        mcTypingTimeline = UiMotion.typewriter(mcDialogueLabel, line, mcTypingTimeline);
        mcSpeechBubble.setStyle("-fx-background-color: rgba(16, 22, 41, 0.97); -fx-padding: 12 16; "
                + "-fx-background-radius: 14; -fx-border-color: " + accentHex + "; -fx-border-width: 2.5; -fx-border-radius: 14;");

        Image targetPose = excitedPose ? mcHypeSprite : mcPresentSprite;
        if (targetPose != null && mcImageView.getImage() != targetPose) {
            mcImageView.setImage(targetPose);
        }
    }

    private void announceClosedByMc(AuctionDto auction) {
        stageAtmosphereOverlay.setStyle("-fx-background-color: rgba(15, 23, 42, 0.56); -fx-background-radius: 10;");
        if (auction.status() == AuctionStatus.CANCELLED) {
            updateMcSpeech("PHIÊN ĐÃ HỦY",
                    "Thông báo từ ban tổ chức: Phiên đấu giá này đã bị hủy và hoàn trả toàn bộ tiền đóng băng!",
                    "#7f8c8d", false);
        } else if (auction.winnerId() != null) {
            String itemTitle = currentItem != null ? "\"" + currentItem.name() + "\"" : "vật phẩm #" + auction.itemId();
            updateMcSpeech("CHỐT ĐƠN!",
                    "Búa đã gõ! Chúc mừng Bidder #" + auction.winnerId() + " đã sở hữu " + itemTitle
                            + " với mức giá chốt " + auction.currentPriceCents() + " cents!",
                    "#27ae60", true);
        } else {
            updateMcSpeech("HẾT GIỜ",
                    "Hết giờ! Phiên đấu giá khép lại mà chưa có nhà sưu tầm nào ra giá.",
                    "#7f8c8d", false);
        }
    }

    private void updateWinnerBanner(AuctionDto auction) {
        long myUserId = NetworkClient.getInstance().userId();
        Long winnerId = auction.winnerId();

        if (auction.status() == AuctionStatus.CANCELLED) {
            winnerTitleLabel.setText("PHIÊN ĐẤU GIÁ ĐÃ BỊ HỦY");
            settlementStatusLabel.setText("Toàn bộ số tiền đóng băng (nếu có) đã được hoàn trả cho người tham gia.");
        } else if (winnerId != null) {
            String suffix = (winnerId == myUserId) ? " (CHÚC MỪNG BẠN ĐÃ THẮNG!)" : "";
            winnerTitleLabel.setText("NGƯỜI THẮNG CUỘC: Bidder #" + winnerId + " • Giá chốt: "
                    + auction.currentPriceCents() + " cents" + suffix);
            if (auction.status() == AuctionStatus.PAID) {
                settlementStatusLabel.setText("Trạng thái quyết toán: ĐÃ THANH TOÁN (PAID) • Đã chuyển tiền cho người bán và hoàn tiền cho người thua.");
            } else {
                settlementStatusLabel.setText("Trạng thái quyết toán: ĐÃ CHỐT PHIÊN (FINISHED) • Đang xử lý thanh toán...");
            }
        } else {
            winnerTitleLabel.setText("PHIÊN ĐẤU GIÁ KẾT THÚC • KHÔNG CÓ NGƯỜI THẮNG");
            settlementStatusLabel.setText("Không có lượt đặt giá hợp lệ nào được ghi nhận trong phiên này.");
        }

        winnerBannerBox.setVisible(true);
        winnerBannerBox.setManaged(true);
    }

    private void updateLeadStatusBadge() {
        if (currentAuction == null) {
            return;
        }
        long myUserId = NetworkClient.getInstance().userId();
        if (isAuctionEnded(currentAuction)) {
            if (currentAuction.status() == AuctionStatus.CANCELLED) {
                setLeadBadge("PHIÊN ĐÃ BỊ HỦY", "#7f8c8d");
                return;
            }
            Long winnerId = currentAuction.winnerId();
            if (winnerId != null && winnerId == myUserId) {
                setLeadBadge("BẠN ĐÃ THẮNG PHIÊN NÀY", "#27ae60");
            } else if (bidRows.stream().anyMatch(b -> b.bidderId() == myUserId)) {
                setLeadBadge("BẠN KHÔNG THẮNG PHIÊN NÀY", "#7f8c8d");
            } else {
                setLeadBadge("PHIÊN ĐÃ ĐÓNG", "#7f8c8d");
            }
            return;
        }

        BidDto activeBid = bidRows.stream()
                .filter(b -> b.status() == BidStatus.ACTIVE)
                .reduce((first, second) -> second)
                .orElse(null);

        if (activeBid != null && activeBid.bidderId() == myUserId) {
            setLeadBadge("BẠN ĐANG DẪN ĐẦU", "#27ae60");
        } else if (bidRows.stream().anyMatch(b -> b.bidderId() == myUserId)) {
            setLeadBadge("BẠN ĐÃ BỊ VƯỢT GIÁ (OUTBID)", "#e67e22");
        } else {
            setLeadBadge("CHƯA THAM GIA ĐẶT GIÁ", "#7f8c8d");
        }
    }

    private void setLeadBadge(String text, String hexColor) {
        leadStatusLabel.setText(text);
        leadStatusLabel.setStyle("-fx-background-color: " + hexColor
                + "; -fx-text-fill: white; -fx-padding: 4 10; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");
    }

    private static String money(long cents) { return String.format(java.util.Locale.US, "%,d cents", cents); }

    private long computeMinValidBid(AuctionDto auction) {
        return Math.addExact(auction.currentPriceCents(), auction.minIncrementCents());
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
            UiMotion.pulse(bidAmountField, 1.03);
        }
    }

    @FXML
    private void handlePlaceBid() {
        if (currentAuction == null) {
            return;
        }
        if (isAuctionEnded(currentAuction)) {
            showBanner("Phiên đấu giá đã kết thúc, không thể đặt giá.", "#e74c3c");
            UiMotion.shake(bidAmountField);
            return;
        }

        long amountCents;
        try {
            amountCents = Long.parseLong(bidAmountField.getText().trim());
        } catch (NumberFormatException e) {
            showBanner("Vui lòng nhập số tiền hợp lệ (số nguyên cents).", "#e74c3c");
            UiMotion.shake(bidAmountField);
            return;
        }

        long minValid = computeMinValidBid(currentAuction);
        if (amountCents < minValid) {
            showBanner("Mức giá tối thiểu hợp lệ hiện tại là " + minValid + " cents.", "#e74c3c");
            UiMotion.shake(bidAmountField);
            return;
        }

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
                    if (disposed) return;
                    placeBidButton.setText("Đặt giá ngay");
                    bidAmountField.clear();
                    showBanner("Đặt giá thành công: " + result.amountCents() + " cents!", "#27ae60");
                    updatePriceLocally(result.currentPriceCents(), result.auctionVersion());
                    playStageHighlight();
                    updateMcSpeech("LÊN GIÁ THÀNH CÔNG!",
                            "Tuyệt vời! Bạn vừa nâng mức giá lên " + result.currentPriceCents()
                                    + " cents và đang làm chủ sân khấu Gala!", "#27ae60", true);
                    if (!isAuctionEnded(currentAuction)) {
                        placeBidButton.setDisable(false);
                    }
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    submittingBid.set(false);
                    if (disposed) return;
                    placeBidButton.setText("Đặt giá ngay");
                    showBanner("Đặt giá thất bại: " + error.getMessage(), "#e74c3c");
                    UiMotion.shake(bidAmountField);
                    if (!isAuctionEnded(currentAuction)) {
                        placeBidButton.setDisable(false);
                    }
                });
            }
        }, "auction-place-bid");
        bidThread.setDaemon(true);
        bidThread.start();
    }

    @FXML
    private void handleSetAutoBid() {
        if (currentAuction == null) {
            return;
        }
        if (isAuctionEnded(currentAuction)) {
            showBanner("Phiên đấu giá đã kết thúc, không thể cài đặt Auto-Bid.", "#e74c3c");
            UiMotion.shake(autoBidMaxField);
            return;
        }

        long maxAmountCents;
        long incrementCents;
        try {
            maxAmountCents = Long.parseLong(autoBidMaxField.getText().trim());
            incrementCents = Long.parseLong(autoBidIncrementField.getText().trim());
        } catch (NumberFormatException e) {
            showBanner("Giá trần và bước giá Auto-Bid phải là số nguyên (cents).", "#e74c3c");
            UiMotion.shake(autoBidMaxField);
            return;
        }

        long minValid = computeMinValidBid(currentAuction);
        if (maxAmountCents < minValid) {
            showBanner("Giá trần Auto-Bid phải lớn hơn hoặc bằng mức đặt hợp lệ tối thiểu (" + minValid + " cents).", "#e74c3c");
            UiMotion.shake(autoBidMaxField);
            return;
        }
        if (incrementCents < currentAuction.minIncrementCents()) {
            showBanner("Bước giá Auto-Bid tối thiểu là " + currentAuction.minIncrementCents() + " cents.", "#e74c3c");
            UiMotion.shake(autoBidIncrementField);
            return;
        }

        if (!submittingAutoBid.compareAndSet(false, true)) {
            return;
        }
        setAutoBidButton.setDisable(true);
        setAutoBidButton.setText("Đang lưu...");
        long auctionId = currentAuction.auctionId();

        Thread autoBidThread = new Thread(() -> {
            try {
                NetworkClient.AutoBidResult result =
                        NetworkClient.getInstance().setAutoBid(auctionId, maxAmountCents, incrementCents);
                Platform.runLater(() -> {
                    submittingAutoBid.set(false);
                    if (disposed) return;
                    setAutoBidButton.setText("Cập nhật Auto-Bid");
                    autoBidStatusLabel.setText("Đang bật • Trần: " + result.maxAmountCents()
                            + " cents | Bước: +" + result.incrementCents() + " cents");
                    autoBidStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-font-weight: bold;");
                    showBanner("Đã kích hoạt Auto-Bid (trần tối đa " + result.maxAmountCents() + " cents).", "#2980b9");
                    updateMcSpeech("TRỢ LÝ AUTO-BID",
                            "Đã ghi nhận lệnh Auto-Bid tới mức trần " + result.maxAmountCents()
                                    + " cents! Cứ có đối thủ vượt mặt là hệ thống tự động đáp trả!",
                            "#2980b9", false);
                    if (!isAuctionEnded(currentAuction)) {
                        setAutoBidButton.setDisable(false);
                    }
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    submittingAutoBid.set(false);
                    if (disposed) return;
                    setAutoBidButton.setText("Kích hoạt Auto-Bid");
                    showBanner("Cài đặt Auto-Bid thất bại: " + error.getMessage(), "#e74c3c");
                    UiMotion.shake(autoBidMaxField);
                    if (!isAuctionEnded(currentAuction)) {
                        setAutoBidButton.setDisable(false);
                    }
                });
            }
        }, "auction-set-autobid");
        autoBidThread.setDaemon(true);
        autoBidThread.start();
    }

    private void onServerEvent(EventEnvelope event) {
        if (disposed || currentAuction == null || event.auctionId() != currentAuction.auctionId()) {
            return;
        }
        if (event.eventType() != EventType.BID_REJECTED) {
            if (event.sequence() <= lastSequence) {
                return;
            }
            lastSequence = event.sequence();
        }

        Platform.runLater(() -> {
            if (disposed) return;
            JsonObject payload = parsePayload(event.payloadJson());
            switch (event.eventType()) {
                case BID_ACCEPTED -> {
                    long newPrice = payload.has("currentPriceCents")
                            ? payload.get("currentPriceCents").getAsLong()
                            : (payload.has("amountCents") ? payload.get("amountCents").getAsLong() : currentAuction.currentPriceCents());
                    long version = payload.has("auctionVersion") ? payload.get("auctionVersion").getAsLong() : currentAuction.version() + 1;
                    long bidderId = payload.has("bidderId") ? payload.get("bidderId").getAsLong() : 0L;
                    long bidId = payload.has("bidId") ? payload.get("bidId").getAsLong() : event.sequence();

                    if (bidderId > 0) {
                        appendBidPoint(new BidDto(bidId, currentAuction.auctionId(), bidderId, newPrice,
                                event.occurredAt(), BidStatus.ACTIVE, event.eventId()));
                    }
                    updatePriceLocally(newPrice, version);
                    playStageHighlight();

                    if (bidderId == NetworkClient.getInstance().userId()) {
                        showBanner("Bạn đang dẫn đầu ở mức " + newPrice + " cents!", "#27ae60");
                        updateMcSpeech("DẪN ĐẦU SÀN!",
                                "Chốt mức giá mới " + newPrice + " cents! Bạn đang dẫn đầu, có ai dám trả cao hơn không nào?!",
                                "#27ae60", true);
                    } else {
                        showBanner("Có lượt đặt giá mới: " + newPrice + " cents!", "#2980b9");
                        updateMcSpeech("CÓ GIÁ MỚI!",
                                "Căng thẳng quá! Bidder #" + bidderId + " vừa đẩy giá lên " + newPrice
                                        + " cents! Ai sẽ lên tiếng tiếp theo đây?",
                                "#2980b9", true);
                    }
                }
                case OUTBID -> {
                    long newPrice = payload.has("currentPriceCents")
                            ? payload.get("currentPriceCents").getAsLong()
                            : currentAuction.currentPriceCents();
                    updatePriceLocally(newPrice, payload.has("auctionVersion") ? payload.get("auctionVersion").getAsLong() : currentAuction.version());
                    setLeadBadge("BẠN ĐÃ BỊ VƯỢT GIÁ (OUTBID)", "#e67e22");
                    showBanner("CẢNH BÁO: Bạn đã bị vượt giá (OUTBID)! Giá hiện tại là " + newPrice + " cents.", "#e67e22");
                    playStageHighlight();
                    updateMcSpeech("BẠN BỊ VƯỢT MẶT!",
                            "Ối! Đối thủ vừa vượt giá của bạn lên " + newPrice
                                    + " cents rồi! Ra giá ngay kẻo tuột mất vật phẩm!",
                            "#e67e22", true);
                }
                case BID_REJECTED -> {
                    String reason = payload.has("reason") ? payload.get("reason").getAsString() : "Lệnh đặt giá bị từ chối.";
                    showBanner("Lệnh đặt giá bị từ chối: " + reason, "#e74c3c");
                    UiMotion.shake(bidAmountField);
                }
                case AUCTION_EXTENDED -> {
                    Instant newEndAt = currentAuction.endAt();
                    if (payload.has("endAt") && !payload.get("endAt").isJsonNull()) {
                        newEndAt = Instant.parse(payload.get("endAt").getAsString());
                    } else if (payload.has("newEndAt") && !payload.get("newEndAt").isJsonNull()) {
                        newEndAt = Instant.parse(payload.get("newEndAt").getAsString());
                    }
                    int nextExtensions = payload.has("extensionCount") && !payload.get("extensionCount").isJsonNull()
                            ? payload.get("extensionCount").getAsInt()
                            : currentAuction.extensionCount() + 1;

                    currentAuction = new AuctionDto(
                            currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                            currentAuction.currentPriceCents(), currentAuction.minIncrementCents(),
                            currentAuction.startAt(), newEndAt, currentAuction.status(),
                            payload.has("auctionVersion") ? payload.get("auctionVersion").getAsLong() : currentAuction.version(), nextExtensions,
                            currentAuction.winnerId(), currentAuction.closedAt(), currentAuction.createdAt());
                    applyAuctionState(currentAuction, null);
                    startCountdown();
                    stageAtmosphereOverlay.setStyle(
                            "-fx-background-color: linear-gradient(to bottom, rgba(126,34,206,0.35), rgba(30,27,75,0.45)); -fx-background-radius: 10;");
                    playStageHighlight();
                    showBanner("Anti-Snipe: Phiên đấu giá vừa được gia hạn thêm thời gian (lần #" + nextExtensions + ")!", "#8e44ad");
                    updateMcSpeech("KỊCH TÍNH PHÚT CHÓT",
                            "ANTI-SNIPE KÍCH HOẠT! Đã gia hạn thêm giờ (lần #" + nextExtensions
                                    + ")! Cuộc đua vẫn chưa ngã ngũ!",
                            "#8e44ad", true);
                }
                case AUCTION_CANCELLED -> {
                    currentAuction = new AuctionDto(
                            currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                            currentAuction.currentPriceCents(), currentAuction.minIncrementCents(),
                            currentAuction.startAt(), currentAuction.endAt(), AuctionStatus.CANCELLED,
                            currentAuction.version(), currentAuction.extensionCount(),
                            null, event.occurredAt(), currentAuction.createdAt());
                    applyAuctionState(currentAuction, null);
                    stopCountdown();
                    showBanner("Phiên đấu giá đã bị hủy. Tiền đóng băng đã được giải phóng.", "#7f8c8d");
                }
                case AUCTION_FINISHED, AUCTION_SETTLED -> {
                    Long winnerId = payload.has("winnerId") && !payload.get("winnerId").isJsonNull()
                            ? payload.get("winnerId").getAsLong() : currentAuction.winnerId();
                    long finalPrice = payload.has("winningAmountCents") && !payload.get("winningAmountCents").isJsonNull()
                            ? payload.get("winningAmountCents").getAsLong() : currentAuction.currentPriceCents();
                    AuctionStatus nextStatus = event.eventType() == EventType.AUCTION_SETTLED
                            ? AuctionStatus.PAID : AuctionStatus.FINISHED;

                    currentAuction = new AuctionDto(
                            currentAuction.auctionId(), currentAuction.itemId(), currentAuction.startPriceCents(),
                            Math.max(currentAuction.currentPriceCents(), finalPrice), currentAuction.minIncrementCents(),
                            currentAuction.startAt(), currentAuction.endAt(), nextStatus,
                            currentAuction.version(), currentAuction.extensionCount(),
                            winnerId, event.occurredAt(), currentAuction.createdAt());
                    applyAuctionState(currentAuction, null);
                    playStageHighlight();
                    String winnerText = (winnerId != null) ? " Người thắng: Bidder #" + winnerId + "." : " Không có người thắng.";
                    showBanner("Phiên đấu giá đã kết thúc (" + nextStatus.name() + ")!" + winnerText, "#2c3e50");
                    refreshDetailFromServer(currentAuction.auctionId());
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
            minNextBidLabel.setText(money(computeMinValidBid(currentAuction)));
            updateLeadStatusBadge();
        }
    }

    private void appendBidPoint(BidDto bid) {
        boolean exists = bidRows.stream().anyMatch(b -> b.bidId() == bid.bidId());
        if (exists) {
            return;
        }
        for (int index = 0; index < bidRows.size(); index++) {
            BidDto previous = bidRows.get(index);
            if (previous.status() == BidStatus.ACTIVE
                    && previous.auctionId() == bid.auctionId()) {
                bidRows.set(index, new BidDto(
                        previous.bidId(), previous.auctionId(), previous.bidderId(),
                        previous.amountCents(), previous.bidTime(), BidStatus.OUTBID,
                        previous.clientRequestId()));
            }
        }
        bidRows.add(bid);
        priceSeries.getData().add(new XYChart.Data<>(TIME_FMT.format(bid.bidTime()), bid.amountCents()));
        updateLeadStatusBadge();
    }

    private void startCountdown() {
        stopCountdown();
        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> updateCountdownTick()));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        updateCountdownTick();
        if (countdownTimeline != null) countdownTimeline.play();
    }

    private void updateCountdownTick() {
        if (currentAuction == null) {
            return;
        }
        Duration remaining = Duration.between(Instant.now(), currentAuction.endAt());
        if (remaining.isNegative() || remaining.isZero() || isAuctionEnded(currentAuction)) {
            countdownLabel.setText("00:00:00 (Đã đóng)");
            countdownLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #fb7185;");
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
            countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #fb7185;");
            UiMotion.pulse(countdownLabel, 1.07);
        } else {
            countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #7dd3fc;");
        }
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void setBidControlsEnabled(boolean enabled) {
        enabled = enabled && NetworkClient.getInstance().isConnected()
                && "BIDDER".equalsIgnoreCase(NetworkClient.getInstance().role());
        bidAmountField.setDisable(!enabled);
        quickFillButton.setDisable(!enabled);
        placeBidButton.setDisable(!enabled || submittingBid.get());
        autoBidMaxField.setDisable(!enabled);
        autoBidIncrementField.setDisable(!enabled);
        setAutoBidButton.setDisable(!enabled || submittingAutoBid.get());
        cancelAutoBidButton.setDisable(!enabled || submittingAutoBid.get());
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

    public void cleanup() {
        disposed = true;
        stopCountdown();
        stopStageFloatAnimation();
        if (mcTypingTimeline != null) {
            mcTypingTimeline.stop();
            mcTypingTimeline = null;
        }
        if (priceCounterTimeline != null) {
            priceCounterTimeline.stop();
            priceCounterTimeline = null;
        }
        NetworkClient.getInstance().removeEventListener(realtimeListener);
        NetworkClient.getInstance().setConnectionStateListener(null);
    }

    @FXML
    private void handleBack() {
        cleanup();
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
    }

    @FXML private void handleCancelAutoBid() {
        if (currentAuction == null || !submittingAutoBid.compareAndSet(false, true)) return;
        long id = currentAuction.auctionId();
        setAutoBidButton.setDisable(true);
        cancelAutoBidButton.setDisable(true);
        Thread worker = new Thread(() -> {
            try {
                NetworkClient.getInstance().cancelAutoBid(id);
                Platform.runLater(() -> { if (!disposed) {
                    autoBidStatusLabel.setText("Đã tắt Auto-Bid");
                    showBanner("Đã tắt đặt giá tự động cho phiên này.", "#285eaa");
                }});
            } catch (Exception error) {
                Platform.runLater(() -> { if (!disposed) showBanner(error.getMessage(), "#a32836"); });
            } finally {
                submittingAutoBid.set(false);
                Platform.runLater(() -> { if (!disposed) setBidControlsEnabled(!isAuctionEnded(currentAuction)); });
            }
        }, "auction-cancel-autobid");
        worker.setDaemon(true); worker.start();
    }
}
