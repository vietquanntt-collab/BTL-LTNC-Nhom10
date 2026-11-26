package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public final class SellerDashboardController {

    private static final long MAX_IMAGE_BYTES = 512L * 1024L;

    @FXML private Label feedbackLabel;
    @FXML private TableView<ItemDto> itemTable;
    @FXML private TableColumn<ItemDto, Long> itemIdCol;
    @FXML private TableColumn<ItemDto, String> itemNameCol;
    @FXML private TableColumn<ItemDto, String> itemTypeCol;
    @FXML private TableColumn<ItemDto, String> itemImageCol;
    @FXML private TableColumn<ItemDto, String> itemDescCol;

    @FXML private TextField itemNameField;
    @FXML private TextField itemTypeField;
    @FXML private TextArea itemDescArea;
    @FXML private Label previewPlaceholderLabel;
    @FXML private ImageView itemPreviewImageView;
    @FXML private Label imageFileLabel;
    @FXML private Button chooseImageBtn;
    @FXML private Button clearImageBtn;
    @FXML private Button createItemBtn;
    @FXML private Button updateItemBtn;
    @FXML private Button deleteItemBtn;

    @FXML private TextField auctionItemIdField;
    @FXML private TextField startPriceField;
    @FXML private TextField minIncrementField;
    @FXML private TextField durationHoursField;
    @FXML private Button createAuctionBtn;

    private final ObservableList<ItemDto> itemRows = FXCollections.observableArrayList();
    private String selectedImageBase64;
    private boolean disposed;

    @FXML
    private void initialize() {
        itemTypeField.setPromptText("Hợp lệ: " + Arrays.toString(ItemType.values()));
        itemTable.setPlaceholder(new Label("Chưa có vật phẩm nào. Hãy tạo vật phẩm mới bên dưới."));

        itemIdCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().itemId()));
        itemNameCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name()));
        itemTypeCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().itemType() != null ? c.getValue().itemType().name() : ""));
        itemImageCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().imageBase64() != null && !c.getValue().imageBase64().isBlank() ? "Có ảnh" : "Tự động"));
        itemDescCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().description()));

        itemTable.setItems(itemRows);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean hasSelection = selected != null;
            updateItemBtn.setDisable(!hasSelection);
            deleteItemBtn.setDisable(!hasSelection);
            if (hasSelection) {
                itemNameField.setText(selected.name());
                itemTypeField.setText(selected.itemType() != null ? selected.itemType().name() : "");
                itemDescArea.setText(selected.description());
                auctionItemIdField.setText(String.valueOf(selected.itemId()));
                selectedImageBase64 = selected.imageBase64();
                Image preview = StageAssetLoader.resolveItemStageImage(selected);
                itemPreviewImageView.setImage(preview);
                previewPlaceholderLabel.setVisible(preview == null);
                imageFileLabel.setText(selected.imageBase64() != null && !selected.imageBase64().isBlank()
                        ? "Ảnh đính kèm của vật phẩm #" + selected.itemId()
                        : "Tự động ghép ảnh sân khấu cho vật phẩm #" + selected.itemId());
            }
        });

        loadItems();
    }

    @FXML
    private void onPresetPinkPanther() {
        applyPreset("pink_panther", "Gấu Bông Báo Hồng Pink Panther", "FIGURE",
                "Phiên bản gấu bông Báo Hồng mềm mịn cao cấp, dáng vẫy tay cực kỳ đáng yêu.");
    }

    @FXML
    private void onPresetDoraemonPink() {
        applyPreset("doraemon_pink", "Gấu Bông Doraemon Hồng Ôm Tim", "FIGURE",
                "Phiên bản Doraemon hồng pastel giới hạn với đôi mắt trái tim ngọt ngào.");
    }

    @FXML
    private void onPresetDoraemonBlue() {
        applyPreset("doraemon_blue", "Gấu Bông Doraemon Cổ Điển", "FIGURE",
                "Mèo máy Doraemon xanh dương kinh điển chất liệu bông nhung siêu êm ái.");
    }

    private void applyPreset(String spriteName, String title, String type, String desc) {
        itemTable.getSelectionModel().clearSelection();
        itemNameField.setText(title);
        itemTypeField.setText(type);
        itemDescArea.setText(desc);
        selectedImageBase64 = StageAssetLoader.loadPresetAsBase64(spriteName);
        Image cutout = StageAssetLoader.loadCutoutSprite(spriteName);
        itemPreviewImageView.setImage(cutout);
        previewPlaceholderLabel.setVisible(cutout == null);
        imageFileLabel.setText("Đã chọn mẫu sân khấu: " + title);
        hideFeedback();
    }

    @FXML
    private void onChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh vật phẩm (.png / .jpg)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Ảnh món đồ (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(itemNameField.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            showError("Ảnh vượt quá 512 KB (" + (file.length() / 1024) + " KB). Vui lòng chọn ảnh gọn hơn.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String base64 = Base64.getEncoder().encodeToString(bytes);
            Image cutout = StageAssetLoader.decodeAndCutoutBase64(base64);
            if (cutout == null) {
                showError("File ảnh không hợp lệ hoặc bị hỏng.");
                return;
            }
            selectedImageBase64 = base64;
            itemPreviewImageView.setImage(cutout);
            previewPlaceholderLabel.setVisible(false);
            imageFileLabel.setText("Đã chọn: " + file.getName() + " (" + Math.max(1, file.length() / 1024) + " KB)");
            hideFeedback();
        } catch (Exception e) {
            showError("Không thể đọc file ảnh: " + e.getMessage());
        }
    }

    @FXML
    private void onClearImage() {
        selectedImageBase64 = null;
        itemPreviewImageView.setImage(null);
        previewPlaceholderLabel.setVisible(true);
        imageFileLabel.setText("Tự động tách nền trắng/đen khi đưa lên sân khấu 2D.");
    }

    @FXML
    private void onRefreshItems() {
        loadItems();
    }

    @FXML
    private void onClearForm() {
        auctionItemIdField.clear();
        itemTable.getSelectionModel().clearSelection();
        itemNameField.clear();
        itemTypeField.clear();
        itemDescArea.clear();
        onClearImage();
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
        String imagePayload = selectedImageBase64;
        createItemBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                long newItemId = NetworkClient.getInstance().createItem(name, type, desc, imagePayload);
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    createItemBtn.setDisable(false);
                    auctionItemIdField.setText(String.valueOf(newItemId));
                    showSuccess("Đã tạo vật phẩm mới thành công (ID #" + newItemId + ").");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
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
        String imagePayload = selectedImageBase64 == null ? "" : selectedImageBase64;
        updateItemBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().updateItem(selected.itemId(), name, type, desc, imagePayload);
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    updateItemBtn.setDisable(false);
                    showSuccess("Đã cập nhật thông tin vật phẩm #" + selected.itemId() + ".");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
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
                    if (disposed) {
                        return;
                    }
                    onClearForm();
                    showSuccess("Đã xóa vật phẩm #" + selected.itemId() + ".");
                    loadItems();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
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

        if (itemId <= 0 || startPrice < 0 || minIncrement <= 0 || durationHours < 1 || durationHours > 720) {
            showError("ID và bước giá phải dương; giá khởi điểm không âm; thời lượng từ 1 đến 720 giờ.");
            return;
        }

        createAuctionBtn.setDisable(true);
        Thread thread = new Thread(() -> {
            try {
                long auctionId = NetworkClient.getInstance()
                        .createAuction(itemId, startPrice, minIncrement, durationHours);
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    createAuctionBtn.setDisable(false);
                    showSuccess("Đã mở phiên đấu giá #" + auctionId + " thành công!");
                    NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
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
                Platform.runLater(() -> {
                    if (!disposed) {
                        itemRows.setAll(items);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!disposed) {
                        showError("Không thể tải danh sách vật phẩm: " + e.getMessage());
                    }
                });
            }
        }, "seller-list-items");
        loader.setDaemon(true);
        loader.start();
    }

    private void showSuccess(String msg) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; "
                + "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void showError(String msg) {
        feedbackLabel.setText(msg);
        feedbackLabel.setStyle("-fx-background-color: #e11d48; -fx-text-fill: white; "
                + "-fx-padding: 10 14; -fx-background-radius: 6; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    public void cleanup() {
        disposed = true;
    }
}
