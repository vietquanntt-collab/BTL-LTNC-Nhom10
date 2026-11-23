package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.model.AuctionStatus;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class AuctionListController {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private ComboBox<String> statusFilterBox;
    @FXML private Button refreshBtn;
    @FXML private Label statusMessageLabel;
    @FXML private TableView<AuctionDto> auctionTable;
    @FXML private TableColumn<AuctionDto, Long> idCol;
    @FXML private TableColumn<AuctionDto, Long> itemCol;
    @FXML private TableColumn<AuctionDto, Long> startPriceCol;
    @FXML private TableColumn<AuctionDto, Long> currentPriceCol;
    @FXML private TableColumn<AuctionDto, Long> incrementCol;
    @FXML private TableColumn<AuctionDto, String> statusCol;
    @FXML private TableColumn<AuctionDto, String> endAtCol;
    @FXML private Button cancelAuctionBtn;
    @FXML private Button openDetailBtn;

    private final ObservableList<AuctionDto> rows = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        statusFilterBox.setItems(FXCollections.observableArrayList(
                "ALL", "OPEN", "RUNNING", "FINISHED", "PAID", "CANCELLED"));
        statusFilterBox.getSelectionModel().select("ALL");

        idCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().auctionId()));
        itemCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().itemId()));
        startPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().startPriceCents()));
        currentPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().currentPriceCents()));
        incrementCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().minIncrementCents()));
        statusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().name()));
        endAtCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(TIME_FMT.format(c.getValue().endAt())));

        auctionTable.setPlaceholder(new Label("Không có phiên đấu giá nào khớp bộ lọc hiện tại."));
        auctionTable.setItems(rows);

        String role = NetworkClient.getInstance().role();
        boolean canCancel = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        cancelAuctionBtn.setVisible(canCancel);
        cancelAuctionBtn.setManaged(canCancel);
        cancelAuctionBtn.setDisable(true);
        openDetailBtn.setDisable(true);

        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            openDetailBtn.setDisable(selected == null);
            if (canCancel) {
                boolean cancellable = selected != null
                        && (selected.status() == AuctionStatus.OPEN || selected.status() == AuctionStatus.RUNNING);
                cancelAuctionBtn.setDisable(!cancellable);
            }
        });

        auctionTable.setRowFactory(tv -> {
            TableRow<AuctionDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openAuctionDetail(row.getItem());
                }
            });
            return row;
        });

        loadAuctions();
    }

    @FXML
    private void onFilterChanged() {
        loadAuctions();
    }

    @FXML
    private void onRefresh() {
        loadAuctions();
    }

    @FXML
    private void onOpenDetail() {
        AuctionDto selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openAuctionDetail(selected);
        }
    }

    @FXML
    private void onCancelAuction() {
        AuctionDto selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        cancelAuctionBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().cancelAuction(selected.auctionId());
                Platform.runLater(() -> {
                    showBanner("Đã hủy phiên đấu giá #" + selected.auctionId() + ".", "#27ae60");
                    loadAuctions();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showBanner("Không thể hủy phiên: " + e.getMessage(), "#e74c3c");
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
            showBanner("Không thể mở chi tiết phiên đấu giá.", "#e74c3c");
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
                    rows.setAll(auctions);
                    if (auctions.isEmpty()) {
                        showBanner("Chưa có phiên đấu giá nào khớp bộ lọc hiện tại.", "#7f8c8d");
                    } else {
                        hideBanner();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    refreshBtn.setDisable(false);
                    showBanner("Lỗi tải danh sách phiên: " + e.getMessage(), "#e74c3c");
                });
            }
        }, "auction-list-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void showBanner(String text, String color) {
        statusMessageLabel.setText(text);
        statusMessageLabel.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        statusMessageLabel.setVisible(true);
        statusMessageLabel.setManaged(true);
    }

    private void hideBanner() {
        statusMessageLabel.setVisible(false);
        statusMessageLabel.setManaged(false);
    }
}