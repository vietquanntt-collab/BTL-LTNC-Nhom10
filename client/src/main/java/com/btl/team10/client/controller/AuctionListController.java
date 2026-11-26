package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.client.util.UiMotion;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.protocol.EventEnvelope;
import com.btl.team10.common.protocol.EventType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class AuctionListController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String FILTER_ALL = "ALL";
    private static final String FILTER_LIVE = "LIVE";
    private static final String FILTER_OPEN = "OPEN";
    private static final String FILTER_ENDED = "ENDED";

    @FXML private Circle liveHeaderDot;
    @FXML private Label kpiTotalLabel;
    @FXML private TextField searchField;
    @FXML private Button toggleLayoutBtn;
    @FXML private Button refreshBtn;

    @FXML private Button chipAllBtn;
    @FXML private Button chipLiveBtn;
    @FXML private Button chipOpenBtn;
    @FXML private Button chipEndedBtn;

    @FXML private Pane tickerViewport;
    @FXML private Label liveTickerLabel;

    @FXML private Pane hallPatternPane;
    @FXML private Label statusMessageLabel;

    @FXML private ScrollPane cardScrollPane;
    @FXML private FlowPane cardGridPane;

    @FXML private TableView<AuctionDto> auctionTable;
    @FXML private TableColumn<AuctionDto, String> idCol;
    @FXML private TableColumn<AuctionDto, String> itemCol;
    @FXML private TableColumn<AuctionDto, String> startPriceCol;
    @FXML private TableColumn<AuctionDto, String> currentPriceCol;
    @FXML private TableColumn<AuctionDto, String> incrementCol;
    @FXML private TableColumn<AuctionDto, String> statusCol;
    @FXML private TableColumn<AuctionDto, String> endAtCol;

    @FXML private HBox bottomActionBar;
    @FXML private Button cancelAuctionBtn;
    @FXML private Button openDetailBtn;

    private final ObservableList<AuctionDto> visibleRows = FXCollections.observableArrayList();
    private final List<AuctionDto> allFetchedAuctions = new ArrayList<>();
    private final Map<Long, ItemDto> itemCache = new ConcurrentHashMap<>();
    private final Map<Long, Integer> bidCountCache = new ConcurrentHashMap<>();
    private final List<Runnable> cardTickers = new ArrayList<>();
    private final List<String> tickerMessages = new ArrayList<>();

    private Timeline cardCountdownTimeline;
    private Timeline tickerMarqueeTimeline;
    private double tickerOffsetX;
    private String activeFilter = FILTER_ALL;
    private AuctionDto selectedAuction;
    private boolean cardViewMode = true;
    private boolean disposed;
    private int loadGeneration;
    private PauseTransition refreshDebounce;

    private final Consumer<EventEnvelope> listEvents = event ->
            Platform.runLater(() -> {
                if (!disposed) {
                    handleRealtimeTickerEvent(event);
                    refreshDebounce.playFromStart();
                }
            });

    private final Consumer<Boolean> reconnectListener = connected ->
            Platform.runLater(() -> {
                if (connected && !disposed) {
                    loadAuctions();
                }
            });

    @FXML
    private void initialize() {
        refreshDebounce = new PauseTransition(javafx.util.Duration.millis(300));
        refreshDebounce.setOnFinished(e -> loadAuctions());
        NetworkClient.getInstance().addEventListener(listEvents);
        NetworkClient.getInstance().addConnectionListener(reconnectListener);

        setupHallSubtleBackdrop();
        setupLiveTicker();
        updateFilterChipStyles();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
        }

        idCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>("#" + c.getValue().auctionId()));
        itemCol.setCellValueFactory(c -> {
            ItemDto item = itemCache.get(c.getValue().itemId());
            String text = item != null ? item.name() : "Gấu bông #" + c.getValue().itemId();
            return new ReadOnlyObjectWrapper<>(text);
        });
        startPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(formatMoney(c.getValue().startPriceCents())));
        currentPriceCol.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>(formatMoney(c.getValue().currentPriceCents())));
        incrementCol.setCellValueFactory(c ->
                new ReadOnlyObjectWrapper<>("+" + formatMoney(c.getValue().minIncrementCents())));
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().name()));
        endAtCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(TIME_FMT.format(c.getValue().endAt())));

        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auctionTable.setPlaceholder(new Label("Không có phiên đấu giá nào khớp bộ lọc hiện tại."));
        auctionTable.setItems(visibleRows);

        String role = NetworkClient.getInstance().role();
        boolean canCancel = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        if (bottomActionBar != null) {
            bottomActionBar.setVisible(canCancel);
            bottomActionBar.setManaged(canCancel);
        }
        cancelAuctionBtn.setVisible(canCancel);
        cancelAuctionBtn.setManaged(canCancel);
        cancelAuctionBtn.setDisable(true);
        openDetailBtn.setDisable(true);

        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) ->
                updateSelectionState(selected));

        auctionTable.setRowFactory(tv -> {
            TableRow<AuctionDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openAuctionDetail(row.getItem());
                }
            });
            return row;
        });

        startCardCountdownTimer();
        loadAuctions();
    }

    private void setupHallSubtleBackdrop() {
        if (hallPatternPane == null) {
            return;
        }
        Image patternImg = StageAssetLoader.loadRawBackdrop("login_bg");
        if (patternImg == null || patternImg.getWidth() <= 0 || patternImg.getHeight() <= 0) {
            return;
        }
        double tileW = patternImg.getWidth() * 0.45;
        double tileH = patternImg.getHeight() * 0.45;
        Rectangle patternRect = new Rectangle();
        patternRect.widthProperty().bind(hallPatternPane.widthProperty());
        patternRect.heightProperty().bind(hallPatternPane.heightProperty());
        patternRect.setFill(new ImagePattern(patternImg, 0, 0, tileW, tileH, false));
        patternRect.setOpacity(0.055);
        patternRect.setMouseTransparent(true);
        hallPatternPane.getChildren().add(patternRect);
    }

    private void setupLiveTicker() {
        if (tickerViewport == null || liveTickerLabel == null) {
            return;
        }
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(tickerViewport.widthProperty());
        clip.heightProperty().bind(tickerViewport.heightProperty());
        tickerViewport.setClip(clip);

        tickerMessages.add("Chào mừng đến với sàn đấu giá Plushie Gala");
        tickerMessages.add("Mẹo: Dùng Auto-Bid trong phòng đấu giá để tự động giữ vị trí dẫn đầu");
        rebuildTickerText();

        tickerOffsetX = 320.0;
        liveTickerLabel.setLayoutX(tickerOffsetX);
        liveTickerLabel.setLayoutY(1.0);

        tickerMarqueeTimeline = new Timeline(new KeyFrame(javafx.util.Duration.millis(25), e -> {
            double viewW = tickerViewport.getWidth() > 0 ? tickerViewport.getWidth() : 700.0;
            double textW = liveTickerLabel.getWidth() > 0 ? liveTickerLabel.getWidth() : 520.0;
            tickerOffsetX -= 1.35;
            if (tickerOffsetX < -textW) {
                tickerOffsetX = viewW;
            }
            liveTickerLabel.setLayoutX(tickerOffsetX);
        }));
        tickerMarqueeTimeline.setCycleCount(Animation.INDEFINITE);
        tickerMarqueeTimeline.play();
    }

    private void rebuildTickerText() {
        if (liveTickerLabel == null || tickerMessages.isEmpty()) {
            return;
        }
        liveTickerLabel.setText(String.join("   •   ", tickerMessages));
    }

    private void pushTickerMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return;
        }
        tickerMessages.add(0, msg);
        while (tickerMessages.size() > 5) {
            tickerMessages.remove(tickerMessages.size() - 1);
        }
        rebuildTickerText();
    }

    private void handleRealtimeTickerEvent(EventEnvelope event) {
        JsonObject payload = parsePayload(event.payloadJson());
        long id = event.auctionId();
        if (event.eventType() == EventType.BID_ACCEPTED) {
            long bidderId = payload.has("bidderId") ? payload.get("bidderId").getAsLong() : 0L;
            long price = payload.has("currentPriceCents")
                    ? payload.get("currentPriceCents").getAsLong()
                    : (payload.has("amountCents") ? payload.get("amountCents").getAsLong() : 0L);
            if (bidderId > 0 && price > 0) {
                pushTickerMessage("Bidder #" + bidderId + " vừa nâng giá phiên #" + id
                        + " lên " + formatMoney(price) + " cents");
            }
        } else if (event.eventType() == EventType.AUCTION_EXTENDED) {
            pushTickerMessage("Anti-Snipe kích hoạt: Phiên #" + id + " vừa được gia hạn thêm giờ!");
        } else if (event.eventType() == EventType.AUCTION_FINISHED || event.eventType() == EventType.AUCTION_SETTLED) {
            Long winnerId = payload.has("winnerId") && !payload.get("winnerId").isJsonNull()
                    ? payload.get("winnerId").getAsLong() : null;
            if (winnerId != null) {
                pushTickerMessage("Chúc mừng Bidder #" + winnerId + " vừa thắng phiên #" + id + "!");
            }
        }
    }

    @FXML
    private void onSelectFilterAll() {
        selectFilter(FILTER_ALL);
    }

    @FXML
    private void onSelectFilterLive() {
        selectFilter(FILTER_LIVE);
    }

    @FXML
    private void onSelectFilterOpen() {
        selectFilter(FILTER_OPEN);
    }

    @FXML
    private void onSelectFilterEnded() {
        selectFilter(FILTER_ENDED);
    }

    private void selectFilter(String filter) {
        this.activeFilter = filter;
        updateFilterChipStyles();
        applySearchFilter();
    }

    private void updateFilterChipStyles() {
        applyChipActiveStyle(chipAllBtn, FILTER_ALL.equals(activeFilter));
        applyChipActiveStyle(chipLiveBtn, FILTER_LIVE.equals(activeFilter));
        applyChipActiveStyle(chipOpenBtn, FILTER_OPEN.equals(activeFilter));
        applyChipActiveStyle(chipEndedBtn, FILTER_ENDED.equals(activeFilter));
    }

    private void applyChipActiveStyle(Button btn, boolean active) {
        if (btn == null) {
            return;
        }
        btn.getStyleClass().remove("filter-chip-active");
        if (active) {
            btn.getStyleClass().add("filter-chip-active");
        }
    }

    private String formatMoney(long cents) {
        return String.format(Locale.US, "%,d", cents);
    }

    private void updateSelectionState(AuctionDto selected) {
        this.selectedAuction = selected;
        openDetailBtn.setDisable(selected == null);
        String role = NetworkClient.getInstance().role();
        boolean canCancel = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        if (canCancel) {
            boolean cancellable = selected != null
                    && (selected.status() == AuctionStatus.OPEN || selected.status() == AuctionStatus.RUNNING);
            cancelAuctionBtn.setDisable(!cancellable);
        }
    }

    @FXML
    private void onToggleLayout() {
        cardViewMode = !cardViewMode;
        cardScrollPane.setVisible(cardViewMode);
        cardScrollPane.setManaged(cardViewMode);
        auctionTable.setVisible(!cardViewMode);
        auctionTable.setManaged(!cardViewMode);
        toggleLayoutBtn.setText(cardViewMode ? "Dạng bảng" : "Dạng thẻ");

        String role = NetworkClient.getInstance().role();
        boolean canCancel = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        if (bottomActionBar != null) {
            boolean showBar = !cardViewMode || canCancel;
            bottomActionBar.setVisible(showBar);
            bottomActionBar.setManaged(showBar);
        }
        UiMotion.animateEntrance(cardViewMode ? cardScrollPane : auctionTable);
    }

    @FXML
    private void onRefresh() {
        UiMotion.pulse(refreshBtn, 1.06);
        loadAuctions();
    }

    @FXML
    private void onOpenDetail() {
        if (selectedAuction != null) {
            openAuctionDetail(selectedAuction);
        }
    }

    @FXML
    private void onCancelAuction() {
        if (selectedAuction == null) {
            return;
        }
        long targetId = selectedAuction.auctionId();
        cancelAuctionBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().cancelAuction(targetId);
                Platform.runLater(() -> {
                    showBanner("Đã hủy phiên đấu giá #" + targetId + ".", "#16a34a");
                    loadAuctions();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showBanner("Không thể hủy phiên: " + e.getMessage(), "#dc2626");
                    cancelAuctionBtn.setDisable(false);
                });
            }
        }, "auction-cancel");
        thread.setDaemon(true);
        thread.start();
    }

    private void openAuctionDetail(AuctionDto selected) {
        AuctionDetailController controller = NavigationManager.getInstance()
                .loadView("/com/btl/team10/client/view/AuctionDetail.fxml", AuctionDetailController.class);
        if (controller != null) {
            controller.initAuction(selected);
        } else {
            showBanner("Không thể mở chi tiết phiên đấu giá.", "#dc2626");
        }
    }

    private void loadAuctions() {
        if (disposed) {
            return;
        }
        int generation = ++loadGeneration;
        refreshBtn.setDisable(true);
        Thread loader = new Thread(() -> {
            try {
                List<AuctionDto> auctions = NetworkClient.getInstance().listAuctions("ALL");
                Platform.runLater(() -> {
                    if (disposed || generation != loadGeneration) {
                        return;
                    }
                    allFetchedAuctions.clear();
                    allFetchedAuctions.addAll(auctions);
                    syncTickerWithLatestAuctions(auctions);
                    applySearchFilter();
                });

                boolean metadataUpdated = false;
                for (AuctionDto auction : auctions) {
                    if (disposed || generation != loadGeneration) {
                        return;
                    }
                    NetworkClient.AuctionDetailData detail =
                            NetworkClient.getInstance().getAuctionDetail(auction.auctionId());
                    if (detail.item() != null) {
                        itemCache.put(auction.itemId(), detail.item());
                        metadataUpdated = true;
                    }
                    List<BidDto> bids = detail.bids();
                    int count = bids != null ? bids.size() : 0;
                    Integer prevCount = bidCountCache.put(auction.auctionId(), count);
                    if (prevCount == null || prevCount != count) {
                        metadataUpdated = true;
                    }
                }

                if (metadataUpdated) {
                    Platform.runLater(() -> {
                        if (disposed || generation != loadGeneration) {
                            return;
                        }
                        syncTickerWithLatestAuctions(allFetchedAuctions);
                        applySearchFilter();
                    });
                }

                Platform.runLater(() -> {
                    if (!disposed && generation == loadGeneration) {
                        refreshBtn.setDisable(false);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed || generation != loadGeneration) {
                        return;
                    }
                    refreshBtn.setDisable(false);
                    showBanner("Lỗi tải danh sách phiên: " + e.getMessage(), "#dc2626");
                });
            }
        }, "auction-list-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void syncTickerWithLatestAuctions(List<AuctionDto> auctions) {
        for (AuctionDto a : auctions) {
            ItemDto item = itemCache.get(a.itemId());
            String name = item != null ? item.name() : "Vật phẩm #" + a.itemId();
            if (a.status() == AuctionStatus.RUNNING) {
                pushTickerMessage("Đang đấu giá: " + name + " (#" + a.auctionId()
                        + ") • Giá hiện tại " + formatMoney(a.currentPriceCents()) + " cents");
            } else if (a.status() == AuctionStatus.PAID && a.winnerId() != null) {
                pushTickerMessage("Phiên #" + a.auctionId() + " (" + name + ") đã chốt cho Bidder #"
                        + a.winnerId() + " ở mức " + formatMoney(a.currentPriceCents()) + " cents");
            }
        }
    }

    private void applySearchFilter() {
        String keyword = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        int allCount = 0;
        int liveCount = 0;
        int openCount = 0;
        int endedCount = 0;

        List<AuctionDto> filtered = new ArrayList<>();

        for (AuctionDto a : allFetchedAuctions) {
            ItemDto cached = itemCache.get(a.itemId());
            String itemName = cached != null && cached.name() != null ? cached.name().toLowerCase(Locale.ROOT) : "";
            String idText = String.valueOf(a.auctionId());
            String itemIdText = String.valueOf(a.itemId());

            if (!keyword.isEmpty()
                    && !itemName.contains(keyword)
                    && !idText.contains(keyword)
                    && !itemIdText.contains(keyword)) {
                continue;
            }

            allCount++;
            boolean isRunning = a.status() == AuctionStatus.RUNNING;
            boolean isOpen = a.status() == AuctionStatus.OPEN;
            boolean isEnded = a.status() == AuctionStatus.FINISHED
                    || a.status() == AuctionStatus.PAID
                    || a.status() == AuctionStatus.CANCELLED;

            if (isRunning) {
                liveCount++;
            }
            if (isOpen) {
                openCount++;
            }
            if (isEnded) {
                endedCount++;
            }

            boolean matchesChip = switch (activeFilter) {
                case FILTER_LIVE -> isRunning;
                case FILTER_OPEN -> isOpen;
                case FILTER_ENDED -> isEnded;
                default -> true;
            };

            if (matchesChip) {
                filtered.add(a);
            }
        }

        if (chipAllBtn != null) {
            chipAllBtn.setText("Tất cả (" + allCount + ")");
        }
        if (chipLiveBtn != null) {
            chipLiveBtn.setText("Đang diễn ra (" + liveCount + ")");
        }
        if (chipOpenBtn != null) {
            chipOpenBtn.setText("Mới mở (" + openCount + ")");
        }
        if (chipEndedBtn != null) {
            chipEndedBtn.setText("Đã kết thúc (" + endedCount + ")");
        }

        visibleRows.setAll(filtered);
        kpiTotalLabel.setText(filtered.size() + " phiên");

        updateSelectionState(null);
        rebuildCardGrid(filtered);

        if (filtered.isEmpty()) {
            showBanner("Chưa có phiên đấu giá nào khớp bộ lọc hiện tại.", "#834463");
        } else {
            hideBanner();
        }
    }

    private void rebuildCardGrid(List<AuctionDto> auctions) {
        cardGridPane.getChildren().clear();
        cardTickers.clear();
        int cardWidth = auctions.size() == 1 ? 320 : 284;
        for (AuctionDto auction : auctions) {
            VBox card = buildAuctionCard(auction, cardWidth);
            cardGridPane.getChildren().add(card);
        }
    }

    private VBox buildAuctionCard(AuctionDto auction, int cardWidth) {
        VBox card = new VBox(12);
        card.setPrefWidth(cardWidth);
        card.setMinWidth(cardWidth);
        card.setPadding(new Insets(16));
        card.setStyle(defaultCardStyle(false));

        boolean isLive = auction.status() == AuctionStatus.RUNNING || auction.status() == AuctionStatus.OPEN;
        ItemDto cached = itemCache.get(auction.itemId());
        int bidCount = bidCountCache.getOrDefault(auction.auctionId(), 0);

        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        if (isLive) {
            Circle liveDot = new Circle(3.5, Color.web("#16a34a"));
            String liveText = auction.status() == AuctionStatus.RUNNING
                    ? "Đang diễn ra"
                    : (!Instant.now().isBefore(auction.startAt()) ? "Đang mở" : "Sắp mở");
            Label liveLbl = new Label(liveText);
            liveLbl.setStyle("-fx-text-fill: #15803d; -fx-font-size: 11px; -fx-font-weight: bold;");
            topRow.getChildren().addAll(liveDot, liveLbl);
        } else {
            String endedText = auction.status() == AuctionStatus.PAID
                    ? "Đã thanh toán"
                    : (auction.status() == AuctionStatus.CANCELLED ? "Đã hủy" : "Đã kết thúc");
            Label statusLbl = new Label(endedText);
            statusLbl.setStyle("-fx-text-fill: #834463; -fx-font-size: 11px; -fx-font-weight: bold;");
            topRow.getChildren().add(statusLbl);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        String typeStr = cached != null && cached.itemType() != null ? cached.itemType().name() : "PLUSH";
        Label metaTag = new Label("#" + auction.auctionId() + " · " + typeStr);
        metaTag.setStyle("-fx-text-fill: #9d5c7c; -fx-font-size: 11px;");
        topRow.getChildren().addAll(spacer, metaTag);

        StackPane artworkBox = new StackPane();
        artworkBox.setPrefHeight(148);
        artworkBox.setStyle("-fx-background-color: linear-gradient(to bottom right, #fdf2f8, #fce7f3); "
                + "-fx-background-radius: 12;");

        ImageView itemImg = new ImageView();
        itemImg.setFitWidth(116);
        itemImg.setFitHeight(116);
        itemImg.setPreserveRatio(true);
        itemImg.setSmooth(true);
        Image sprite = StageAssetLoader.resolveItemStageImage(cached);
        if (sprite != null) {
            itemImg.setImage(sprite);
        }

        String cornerText = (bidCount >= 3 || auction.extensionCount() > 0)
                ? "HOT"
                : (isLive ? "MỚI LÊN SÀN" : "ĐÃ CHỐT");
        String cornerBg = "HOT".equals(cornerText)
                ? "#e11d48"
                : (isLive ? "#db2777" : "#9d5c7c");
        Label cornerBadge = new Label(cornerText);
        cornerBadge.setStyle("-fx-background-color: " + cornerBg
                + "; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 7; "
                + "-fx-font-size: 10px; -fx-font-weight: bold;");
        StackPane.setAlignment(cornerBadge, Pos.TOP_LEFT);
        StackPane.setMargin(cornerBadge, new Insets(8, 0, 0, 8));

        Label bidCountBadge = new Label(bidCount + " lượt trả giá");
        bidCountBadge.setStyle("-fx-background-color: rgba(255, 255, 255, 0.92); -fx-text-fill: #831843; "
                + "-fx-padding: 2 8; -fx-background-radius: 7; "
                + "-fx-font-size: 10.5px; -fx-font-weight: bold;");
        StackPane.setAlignment(bidCountBadge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(bidCountBadge, new Insets(0, 8, 8, 0));

        artworkBox.getChildren().addAll(itemImg, cornerBadge, bidCountBadge);

        UiMotion.attachHoverLift(card);
        card.hoverProperty().addListener((obs, wasHovered, isHovered) -> {
            ScaleTransition scale = new ScaleTransition(javafx.util.Duration.millis(180), itemImg);
            scale.setToX(isHovered ? 1.07 : 1.0);
            scale.setToY(isHovered ? 1.07 : 1.0);

            RotateTransition rotate = new RotateTransition(javafx.util.Duration.millis(180), itemImg);
            rotate.setToAngle(isHovered ? -4.0 : 0.0);

            new ParallelTransition(scale, rotate).play();
        });

        VBox infoBox = new VBox(3);
        Label titleLabel = new Label(cached != null ? cached.name() : "Gấu bông #" + auction.itemId());
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-text-fill: #2d0a1e; -fx-font-size: 15.5px; -fx-font-weight: bold;");

        Label subLabel = new Label("Khởi điểm " + formatMoney(auction.startPriceCents())
                + " · Bước +" + formatMoney(auction.minIncrementCents()));
        subLabel.setStyle("-fx-text-fill: #834463; -fx-font-size: 11.5px;");
        infoBox.getChildren().addAll(titleLabel, subLabel);

        HBox metricsRow = new HBox(8);
        metricsRow.setAlignment(Pos.BOTTOM_LEFT);
        metricsRow.setPadding(new Insets(4, 0, 0, 0));

        VBox priceBox = new VBox(1);
        HBox.setHgrow(priceBox, Priority.ALWAYS);
        Label pCap = new Label("Giá hiện tại");
        pCap.setStyle("-fx-text-fill: #834463; -fx-font-size: 10.5px;");
        Label pVal = new Label(formatMoney(auction.currentPriceCents()) + " cents");
        pVal.setStyle("-fx-text-fill: " + (isLive ? "#db2777" : "#3b0d24")
                + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        priceBox.getChildren().addAll(pCap, pVal);

        Label tVal = new Label("");
        tVal.setStyle("-fx-text-fill: #834463; -fx-font-size: 11.5px; -fx-font-weight: bold;");
        metricsRow.getChildren().addAll(priceBox, tVal);

        Runnable tick = () -> {
            Duration rem = Duration.between(Instant.now(), auction.endAt());
            if (!isLive || rem.isNegative() || rem.isZero()) {
                tVal.setText("");
            } else {
                long sec = rem.getSeconds();
                tVal.setText(String.format("Còn %02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60));
                tVal.setStyle("-fx-text-fill: " + (sec <= 60 ? "#e11d48" : "#9d174d")
                        + "; -fx-font-size: 11.5px; -fx-font-weight: bold;");
            }
        };
        tick.run();
        cardTickers.add(tick);

        Button enterBtn = new Button(isLive ? "Tham gia đấu giá →" : "Xem chi tiết");
        enterBtn.setMaxWidth(Double.MAX_VALUE);
        enterBtn.setPrefHeight(35);
        enterBtn.setStyle(isLive
                ? "-fx-background-color: #db2777; -fx-text-fill: white; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 9; -fx-cursor: hand;"
                : "-fx-background-color: #fce7f3; -fx-text-fill: #831843; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 9; -fx-cursor: hand;");
        enterBtn.setOnAction(e -> openAuctionDetail(auction));

        card.getChildren().addAll(topRow, artworkBox, infoBox, metricsRow, enterBtn);
        card.setOnMouseClicked(e -> {
            updateSelectionState(auction);
            auctionTable.getSelectionModel().select(auction);
            highlightSelectedCard(card);
            if (e.getClickCount() == 2) {
                openAuctionDetail(auction);
            }
        });

        return card;
    }

    private void highlightSelectedCard(VBox activeCard) {
        for (var node : cardGridPane.getChildren()) {
            if (node instanceof VBox box) {
                box.setStyle(defaultCardStyle(box == activeCard));
            }
        }
    }

    private String defaultCardStyle(boolean selected) {
        String border = selected ? "#db2777" : "#f5d0e3";
        String width = selected ? "1.6" : "1";
        return "-fx-background-color: #ffffff; -fx-background-radius: 14; -fx-border-color: "
                + border + "; -fx-border-width: " + width + "; -fx-border-radius: 14;";
    }

    private void startCardCountdownTimer() {
        if (cardCountdownTimeline != null) {
            cardCountdownTimeline.stop();
        }
        cardCountdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            for (Runnable tick : cardTickers) {
                tick.run();
            }
        }));
        cardCountdownTimeline.setCycleCount(Animation.INDEFINITE);
        cardCountdownTimeline.play();
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
        if (refreshDebounce != null) {
            refreshDebounce.stop();
        }
        NetworkClient.getInstance().removeEventListener(listEvents);
        NetworkClient.getInstance().removeConnectionListener(reconnectListener);
        if (cardCountdownTimeline != null) {
            cardCountdownTimeline.stop();
            cardCountdownTimeline = null;
        }
        if (tickerMarqueeTimeline != null) {
            tickerMarqueeTimeline.stop();
            tickerMarqueeTimeline = null;
        }
    }

    private void showBanner(String text, String color) {
        statusMessageLabel.setText(text);
        statusMessageLabel.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 10 14; "
                + "-fx-background-radius: 8; -fx-font-weight: bold;");
        statusMessageLabel.setVisible(true);
        statusMessageLabel.setManaged(true);
    }

    private void hideBanner() {
        statusMessageLabel.setVisible(false);
        statusMessageLabel.setManaged(false);
    }
}
