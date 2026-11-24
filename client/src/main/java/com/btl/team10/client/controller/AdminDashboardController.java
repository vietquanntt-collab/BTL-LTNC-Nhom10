package com.btl.team10.client.controller;

import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.AuctionStatus;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public final class AdminDashboardController {

    @FXML private Button refreshAllBtn;
    @FXML private Label adminFeedbackLabel;

    @FXML private TableView<UserDto> userTable;
    @FXML private TableColumn<UserDto, Long> userIdCol;
    @FXML private TableColumn<UserDto, String> accountCol;
    @FXML private TableColumn<UserDto, String> roleCol;
    @FXML private TableColumn<UserDto, Long> balanceCol;
    @FXML private TableColumn<UserDto, Long> frozenCol;
    @FXML private TableColumn<UserDto, Long> availableCol;

    @FXML private TableView<AuctionDto> auctionTable;
    @FXML private TableColumn<AuctionDto, Long> auctionIdCol;
    @FXML private TableColumn<AuctionDto, Long> auctionItemIdCol;
    @FXML private TableColumn<AuctionDto, Long> auctionPriceCol;
    @FXML private TableColumn<AuctionDto, String> auctionStatusCol;
    @FXML private TableColumn<AuctionDto, Integer> auctionExtCol;
    @FXML private TableColumn<AuctionDto, String> auctionWinnerCol;
    @FXML private Button cancelAuctionBtn;

    private final ObservableList<UserDto> userRows = FXCollections.observableArrayList();
    private final ObservableList<AuctionDto> auctionRows = FXCollections.observableArrayList();
    private boolean disposed;

    @FXML
    private void initialize() {
        userIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().userId()));
        accountCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().account()));
        roleCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().role() != null ? c.getValue().role().toString() : ""));
        balanceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().balanceCents()));
        frozenCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().frozenCents()));
        availableCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                Math.max(0L, c.getValue().balanceCents() - c.getValue().frozenCents())));

        auctionIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().auctionId()));
        auctionItemIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().itemId()));
        auctionPriceCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().currentPriceCents()));
        auctionStatusCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status().name()));
        auctionExtCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().extensionCount()));
        auctionWinnerCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().winnerId() != null ? "Bidder #" + c.getValue().winnerId() : "-"));

        userTable.setPlaceholder(new Label("Chưa có dữ liệu người dùng."));
        auctionTable.setPlaceholder(new Label("Chưa có phiên đấu giá nào trong hệ thống."));
        userTable.setItems(userRows);
        auctionTable.setItems(auctionRows);
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        auctionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean cancellable = selected != null
                    && (selected.status() == AuctionStatus.OPEN || selected.status() == AuctionStatus.RUNNING);
            cancelAuctionBtn.setDisable(!cancellable);
        });

        loadAllData();
    }

    @FXML
    private void onRefreshAll() {
        loadAllData();
    }

    @FXML
    private void onCancelSelectedAuction() {
        AuctionDto selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        cancelAuctionBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().cancelAuction(selected.auctionId());
                Platform.runLater(() -> {
                    if (disposed) return;
                    showBanner("Admin đã hủy phiên đấu giá #" + selected.auctionId() + ".", "#27ae60");
                    loadAllData();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    showBanner("Hủy phiên thất bại: " + e.getMessage(), "#e74c3c");
                    cancelAuctionBtn.setDisable(false);
                });
            }
        }, "admin-cancel-auction");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadAllData() {
        refreshAllBtn.setDisable(true);
        Thread loader = new Thread(() -> {
            try {
                List<UserDto> users = NetworkClient.getInstance().adminListUsers();
                List<AuctionDto> auctions = NetworkClient.getInstance().listAuctions("ALL");
                Platform.runLater(() -> {
                    if (disposed) return;
                    refreshAllBtn.setDisable(false);
                    userRows.setAll(users);
                    auctionRows.setAll(auctions);
                    hideBanner();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    refreshAllBtn.setDisable(false);
                    showBanner("Không thể tải dữ liệu quản trị: " + e.getMessage(), "#e74c3c");
                });
            }
        }, "admin-load-data");
        loader.setDaemon(true);
        loader.start();
    }

    private void showBanner(String msg, String color) {
        adminFeedbackLabel.setText(msg);
        adminFeedbackLabel.setStyle("-fx-background-color: " + color
                + "; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        adminFeedbackLabel.setVisible(true);
        adminFeedbackLabel.setManaged(true);
    }

    private void hideBanner() {
        adminFeedbackLabel.setVisible(false);
        adminFeedbackLabel.setManaged(false);
    }

    public void cleanup() { disposed = true; }
}
