package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Arrays;
import java.util.List;

public final class SellerDashboardController {

    @FXML private Label feedbackLabel;
    @FXML private TableView<ItemDto> itemTable;
    @FXML private TableColumn<ItemDto, Long> itemIdCol;
    @FXML private TableColumn<ItemDto, String> itemNameCol;
    @FXML private TableColumn<ItemDto, String> itemTypeCol;
    @FXML private TableColumn<ItemDto, String> itemDescCol;

    @FXML private TextField itemNameField;
    @FXML private TextField itemTypeField;
    @FXML private TextArea itemDescArea;
    @FXML private Button createItemBtn;
    @FXML private Button updateItemBtn;
    @FXML private Button deleteItemBtn;

    @FXML private TextField auctionItemIdField;
    @FXML private TextField startPriceField;
    @FXML private TextField minIncrementField;
    @FXML private TextField durationHoursField;
    @FXML private Button createAuctionBtn;

    private final ObservableList<ItemDto> itemRows = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        itemTypeField.setPromptText("Hợp lệ: " + Arrays.toString(ItemType.values()));

        itemIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().itemId()));
        itemNameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        itemTypeCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().itemType() != null ? c.getValue().itemType().name() : ""));
        itemDescCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().description()));

        itemTable.setItems(itemRows);
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean hasSelection = selected != null;
            updateItemBtn.setDisable(!hasSelection);
            deleteItemBtn.setDisable(!hasSelection);
            if (hasSelection) {
                itemNameField.setText(selected.name());
                itemTypeField.setText(selected.itemType() != null ? selected.itemType().name() : "");
                itemDescArea.setText(selected.description());
                auctionItemIdField.setText(String.valueOf(selected.itemId()));
            }
        });

        loadItems();
    }

    @FXML
    private void onRefreshItems() {
        loadItems();
    }

    @FXML
    private void onClearForm() {
        itemTable.getSelectionModel().clearSelection();
        itemNameField.clear();
        itemTypeField.clear();
        itemDescArea.clear();
        hideFeedback();
    }

    @FXML
    private void onCreateItem() {
        String name = itemNameField.getText().trim();
        String type = itemTypeField.getText().trim().toUpperCase();
        String desc = itemDescArea.getText().trim();
        if (name.isBlank() || type.isBlank()) {
            showError("Tên vật phẩm và phân loại không được để trống.");
            return;
        }
        createItemBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                long newItemId = NetworkClient.getInstance().createItem(name, type, desc);
                Platform.runLater(() -> {
                    createItemBtn.setDisable(false);
                    auctionItemIdField.setText(String.valueOf(newItemId));
                    showSuccess("Đã tạo vật phẩm mới thành công (ID #" + newItemId + ").");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    createItemBtn.setDisable(false);
                    showError("Tạo vật phẩm thất bại: " + e.getMessage());
                });
            }
        }, "seller-create-item");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onUpdateItem() {
        ItemDto selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        String name = itemNameField.getText().trim();
        String type = itemTypeField.getText().trim().toUpperCase();
        String desc = itemDescArea.getText().trim();
        if (name.isBlank() || type.isBlank()) {
            showError("Tên vật phẩm và phân loại không được để trống.");
            return;
        }
        updateItemBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().updateItem(selected.itemId(), name, type, desc);
                Platform.runLater(() -> {
                    updateItemBtn.setDisable(false);
                    showSuccess("Đã cập nhật vật phẩm #" + selected.itemId() + ".");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateItemBtn.setDisable(false);
                    showError("Cập nhật vật phẩm thất bại: " + e.getMessage());
                });
            }
        }, "seller-update-item");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onDeleteItem() {
        ItemDto selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        deleteItemBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().deleteItem(selected.itemId());
                Platform.runLater(() -> {
                    onClearForm();
                    showSuccess("Đã xóa vật phẩm #" + selected.itemId() + ".");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    deleteItemBtn.setDisable(false);
                    showError("Xóa vật phẩm thất bại: " + e.getMessage());
                });
            }
        }, "seller-delete-item");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onCreateAuction() {
        long itemId;
        long startPrice;
        long minIncrement;
        long durationHours;
        try {
            itemId = Long.parseLong(auctionItemIdField.getText().trim());
            startPrice = Long.parseLong(startPriceField.getText().trim());
            minIncrement = Long.parseLong(minIncrementField.getText().trim());
            durationHours = Long.parseLong(durationHoursField.getText().trim());
        } catch (NumberFormatException e) {
            showError("ID vật phẩm, giá khởi điểm, bước giá và thời lượng phải là số nguyên hợp lệ.");
            return;
        }

        if (startPrice <= 0 || minIncrement <= 0 || durationHours <= 0) {
            showError("Giá khởi điểm, bước giá và thời lượng phải lớn hơn 0.");
            return;
        }

        createAuctionBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                long auctionId = NetworkClient.getInstance()
                        .createAuction(itemId, startPrice, minIncrement, durationHours);
                Platform.runLater(() -> {
                    createAuctionBtn.setDisable(false);
                    showSuccess("Đã mở phiên đấu giá #" + auctionId + " thành công! Đang chuyển sang danh sách phiên...");
                    NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    createAuctionBtn.setDisable(false);
                    showError("Mở phiên đấu giá thất bại: " + e.getMessage());
                });
            }
        }, "seller-create-auction");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadItems() {
        Thread loader = new Thread(() -> {
            try {
                List<ItemDto> items = NetworkClient.getInstance().listItems();
                Platform.runLater(() -> itemRows.setAll(items));
            } catch (Exception e) {
                Platform.runLater(() -> showError("Không thể tải danh sách vật phẩm: " + e.getMessage()));
            }
        }, "seller-list-items");
        loader.setDaemon(true);
        loader.start();
    }

    private void showSuccess(String msg) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void showError(String msg) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}