package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.client.util.UiMotion;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.AuctionStatus;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionListController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Circle liveHeaderDot;
    @FXML private Label totalCountBadge;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilterBox;
    @FXML private Button toggleLayoutBtn;
    @FXML private Button refreshBtn;

    @FXML private Label kpiLiveLabel;
    @FXML private Label kpiVolumeLabel;
    @FXML private Label kpiTotalLabel;
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

    @FXML private Button cancelAuctionBtn;
    @FXML private Button openDetailBtn;

    private final ObservableList<AuctionDto> visibleRows = FXCollections.observableArrayList();
    private final List<AuctionDto> allFetchedAuctions = new ArrayList<>();
    private final Map<Long, ItemDto> itemCache = new ConcurrentHashMap<>();
    private final List<Runnable> cardTickers = new ArrayList<>();

    private Timeline cardCountdownTimeline;
    private AuctionDto selectedAuction;
    private boolean cardViewMode = true;

    @FXML
    private void initialize() {
        UiMotion.startLiveBlink(liveHeaderDot);

        statusFilterBox.setItems(FXCollections.observableArrayList(
                "ALL", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELLED"));
        statusFilterBox.getSelectionModel().select("ALL");

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
        }

        idCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>("#" + c.getValue().auctionId()));
        itemCol.setCellValueFactory(c -> {
            ItemDto item = itemCache.get(c.getValue().itemId());
            String text = item != null ? item.name() : "Vật phẩm #" + c.getValue().itemId();
            return new ReadOnlyObjectWrapper<>(text);
        });
        startPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(formatMoney(c.getValue().startPriceCents())));
        currentPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(formatMoney(c.getValue().currentPriceCents())));
        incrementCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>("+" + formatMoney(c.getValue().minIncrementCents())));
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().name()));
        endAtCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(TIME_FMT.format(c.getValue().endAt())));

        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        auctionTable.setPlaceholder(new Label("Không có phiên đấu giá nào khớp bộ lọc hiện tại."));
        auctionTable.setItems(visibleRows);

        String role = NetworkClient.getInstance().role();
        boolean canCancel = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
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
        UiMotion.animateEntrance(cardViewMode ? cardScrollPane : auctionTable);
    }

    @FXML
    private void onFilterChanged() {
        loadAuctions();
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
        String filter = statusFilterBox.getValue();
        refreshBtn.setDisable(true);
        Thread loader = new Thread(() -> {
            try {
                List<AuctionDto> auctions = NetworkClient.getInstance().listAuctions(filter);
                Platform.runLater(() -> {
                    refreshBtn.setDisable(false);
                    allFetchedAuctions.clear();
                    allFetchedAuctions.addAll(auctions);
                    applySearchFilter();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    refreshBtn.setDisable(false);
                    showBanner("Lỗi tải danh sách phiên: " + e.getMessage(), "#dc2626");
                });
            }
        }, "auction-list-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void applySearchFilter() {
        String keyword = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        List<AuctionDto> filtered = new ArrayList<>();
        long liveCount = 0;
        long totalVolume = 0;

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

            filtered.add(a);
            if (a.status() == AuctionStatus.RUNNING || a.status() == AuctionStatus.OPEN) {
                liveCount++;
            }
            totalVolume += a.currentPriceCents();
        }

        visibleRows.setAll(filtered);
        totalCountBadge.setText(filtered.size() + " phiên");
        if (kpiLiveLabel != null) kpiLiveLabel.setText(liveCount + " phiên");
        if (kpiVolumeLabel != null) kpiVolumeLabel.setText(formatMoney(totalVolume) + " cents");
        if (kpiTotalLabel != null) kpiTotalLabel.setText(filtered.size() + " phiên");

        updateSelectionState(null);
        rebuildCardGrid(filtered);

        if (filtered.isEmpty()) {
            showBanner("Chưa có phiên đấu giá nào khớp bộ lọc hiện tại.", "#475569");
        } else {
            hideBanner();
        }
    }

    private void rebuildCardGrid(List<AuctionDto> auctions) {
        cardGridPane.getChildren().clear();
        cardTickers.clear();
        for (AuctionDto auction : auctions) {
            VBox card = buildAuctionCard(auction);
            cardGridPane.getChildren().add(card);
            fetchItemMetadataForCard(auction, card);
        }
    }

    private VBox buildAuctionCard(AuctionDto auction) {
        VBox card = new VBox(10);
        card.setPrefWidth(258);
        card.setPadding(new Insets(14));
        card.setStyle(defaultCardStyle(false));
        card.setEffect(new DropShadow(14, Color.rgb(15, 23, 42, 0.10)));

        // Hàng 1: Badge trạng thái LIVE + ID phiên
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        boolean isLive = auction.status() == AuctionStatus.RUNNING || auction.status() == AuctionStatus.OPEN;
        HBox statusPill = new HBox(5);
        statusPill.setAlignment(Pos.CENTER_LEFT);
        String pillBg = isLive ? "#dcfce7" : (auction.status() == AuctionStatus.PAID ? "#dbeafe" : "#f1f5f9");
        String pillFg = isLive ? "#15803d" : (auction.status() == AuctionStatus.PAID ? "#1d4ed8" : "#475569");
        statusPill.setStyle("-fx-background-color: " + pillBg + "; -fx-padding: 3 9; -fx-background-radius: 12;");

        if (isLive) {
            Circle liveDot = new Circle(4, Color.web("#16a34a"));
            UiMotion.startLiveBlink(liveDot);
            statusPill.getChildren().add(liveDot);
        }
        Label statusText = new Label(isLive ? "LIVE • " + auction.status().name() : auction.status().name());
        statusText.setStyle("-fx-text-fill: " + pillFg + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        statusPill.getChildren().add(statusText);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label idBadge = new Label("PHIÊN #" + auction.auctionId());
        idBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #334155; -fx-padding: 2 8; -fx-background-radius: 6; -fx-font-size: 10px; -fx-font-weight: bold;");

        topRow.getChildren().addAll(statusPill, spacer, idBadge);

        // Hàng 2: Bục trưng bày vật phẩm Gala (nền tối ánh vàng)
        StackPane artworkBox = new StackPane();
        artworkBox.setPrefHeight(132);
        artworkBox.setStyle("-fx-background-color: radial-gradient(center 50% 45%, radius 70%, #2c345e, #121629); -fx-background-radius: 12; -fx-border-color: rgba(250,204,21,0.35); -fx-border-radius: 12;");

        Circle spotlight = new Circle(46, Color.web("#facc15", 0.14));
        ImageView itemImg = new ImageView();
        itemImg.setFitWidth(104);
        itemImg.setFitHeight(104);
        itemImg.setPreserveRatio(true);

        ItemDto cached = itemCache.get(auction.itemId());
        Image sprite = StageAssetLoader.resolveItemStageImage(cached);
        if (sprite != null) {
            itemImg.setImage(sprite);
        }

        Label typeTag = new Label(cached != null && cached.itemType() != null ? cached.itemType().name() : "ITEM #" + auction.itemId());
        typeTag.setStyle("-fx-background-color: rgba(15,23,42,0.75); -fx-text-fill: #fde047; -fx-padding: 2 8; -fx-background-radius: 6; -fx-font-size: 9px; -fx-font-weight: bold;");
        StackPane.setAlignment(typeTag, Pos.TOP_RIGHT);
        StackPane.setMargin(typeTag, new Insets(8, 8, 0, 0));

        artworkBox.getChildren().addAll(spotlight, itemImg, typeTag);

        // Hàng 3: Tên vật phẩm & thông số khởi điểm
        Label titleLabel = new Label(cached != null ? cached.name() : "Vật phẩm #" + auction.itemId());
        titleLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label subLabel = new Label("Khởi điểm: " + formatMoney(auction.startPriceCents())
                + " • Bước: +" + formatMoney(auction.minIncrementCents()));
        subLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        // Hàng 4: Giá hiện tại & Đồng hồ đếm ngược
        HBox metricsRow = new HBox(8);
        metricsRow.setAlignment(Pos.CENTER_LEFT);
        metricsRow.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 9; -fx-padding: 8 10; -fx-background-radius: 9;");

        VBox priceBox = new VBox(2);
        HBox.setHgrow(priceBox, Priority.ALWAYS);
        Label pCap = new Label("GIÁ HIỆN TẠI");
        pCap.setStyle("-fx-text-fill: #64748b; -fx-font-size: 9px; -fx-font-weight: bold;");
        Label pVal = new Label(formatMoney(auction.currentPriceCents()) + " c");
        pVal.setStyle("-fx-text-fill: #d97706; -fx-font-size: 15px; -fx-font-weight: bold;");
        priceBox.getChildren().addAll(pCap, pVal);

        VBox timeBox = new VBox(2);
        timeBox.setAlignment(Pos.CENTER_RIGHT);
        Label tCap = new Label("THỜI GIAN");
        tCap.setStyle("-fx-text-fill: #64748b; -fx-font-size: 9px; -fx-font-weight: bold;");
        Label tVal = new Label("--:--:--");
        tVal.setStyle("-fx-text-fill: #1d4ed8; -fx-font-size: 13px; -fx-font-weight: bold;");
        timeBox.getChildren().addAll(tCap, tVal);

        metricsRow.getChildren().addAll(priceBox, timeBox);

        Runnable tick = () -> {
            Duration rem = Duration.between(Instant.now(), auction.endAt());
            if (!isLive || rem.isNegative() || rem.isZero()) {
                tVal.setText(auction.status() == AuctionStatus.PAID ? "Đã thanh toán" : "Đã kết thúc");
                tVal.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-font-weight: bold;");
            } else {
                long sec = rem.getSeconds();
                tVal.setText(String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60));
                if (sec <= 60) {
                    tVal.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 13px; -fx-font-weight: bold;");
                }
            }
        };
        tick.run();
        cardTickers.add(tick);

        // Hàng 5: Nút lên sàn đấu giá
        Button enterBtn = new Button(isLive ? "Vào Phòng Đấu Giá →" : "Xem Kết Quả Phiên →");
        enterBtn.setMaxWidth(Double.MAX_VALUE);
        enterBtn.setPrefHeight(34);
        enterBtn.setStyle(isLive
                ? "-fx-background-color: linear-gradient(to right, #16a34a, #15803d); -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;"
                : "-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-size: 12px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
        enterBtn.setOnAction(e -> openAuctionDetail(auction));

        card.getChildren().addAll(topRow, artworkBox, titleLabel, subLabel, metricsRow, enterBtn);
        card.getProperties().put("itemImg", itemImg);
        card.getProperties().put("titleLabel", titleLabel);
        card.getProperties().put("typeTag", typeTag);

        UiMotion.attachHoverLift(card);
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

    private void fetchItemMetadataForCard(AuctionDto auction, VBox card) {
        if (itemCache.containsKey(auction.itemId())) {
            return;
        }
        Thread metaLoader = new Thread(() -> {
            try {
                NetworkClient.AuctionDetailData detail = NetworkClient.getInstance().getAuctionDetail(auction.auctionId());
                ItemDto item = detail.item();
                if (item != null) {
                    itemCache.put(auction.itemId(), item);
                    Platform.runLater(() -> {
                        if (card.getProperties().get("itemImg") instanceof ImageView iv) {
                            Image resolved = StageAssetLoader.resolveItemStageImage(item);
                            if (resolved != null) {
                                iv.setImage(resolved);
                            }
                        }
                        if (card.getProperties().get("titleLabel") instanceof Label tl) {
                            tl.setText(item.name());
                        }
                        if (card.getProperties().get("typeTag") instanceof Label tag && item.itemType() != null) {
                            tag.setText(item.itemType().name());
                        }
                        auctionTable.refresh();
                    });
                }
            } catch (Exception ignored) {
            }
        }, "card-item-loader-" + auction.auctionId());
        metaLoader.setDaemon(true);
        metaLoader.start();
    }

    private void highlightSelectedCard(VBox activeCard) {
        for (var node : cardGridPane.getChildren()) {
            if (node instanceof VBox box) {
                box.setStyle(defaultCardStyle(box == activeCard));
            }
        }
    }

    private String defaultCardStyle(boolean selected) {
        String border = selected ? "#f59e0b" : "#cbd5e1";
        String width = selected ? "2" : "1";
        return "-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: "
                + border + "; -fx-border-width: " + width + "; -fx-border-radius: 14;";
    }

    private void startCardCountdownTimer() {
        cleanup();
        cardCountdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), e -> {
            for (Runnable tick : cardTickers) {
                tick.run();
            }
        }));
        cardCountdownTimeline.setCycleCount(Animation.INDEFINITE);
        cardCountdownTimeline.play();
    }

    public void cleanup() {
        if (cardCountdownTimeline != null) {
            cardCountdownTimeline.stop();
            cardCountdownTimeline = null;
        }
    }

    private void showBanner(String text, String color) {
        statusMessageLabel.setText(text);
        statusMessageLabel.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 8; -fx-font-weight: bold;");
        statusMessageLabel.setVisible(true);
        statusMessageLabel.setManaged(true);
    }

    private void hideBanner() {
        statusMessageLabel.setVisible(false);
        statusMessageLabel.setManaged(false);
    }
}