package com.btl.n8.Controller;

import com.btl.n8.DTO.AddItemRequest;
import com.btl.n8.DTO.AddItemResponse;
import com.btl.n8.DTO.GetSellerItemsRequest;
import com.btl.n8.DTO.GetSellerItemsResponse;
import com.btl.n8.Model.Entity.Auction;
import com.btl.n8.Model.Entity.Item;
import com.btl.n8.Model.Entity.User;
import com.btl.n8.Model.Enums.ItemType;
import com.btl.n8.Model.Enums.Role;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.FileUtils;
import com.btl.n8.Util.ItemTypeAdapter;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;

public class SellController implements ServerResponseListener {

    @FXML private TextField          nameField;
    @FXML private ComboBox<String>   typeCombo;
    @FXML private TextField          priceField;
    @FXML private Spinner<Integer>   hoursSpinner;
    @FXML private Spinner<Integer>   minutesSpinner;
    @FXML private Label              uploadLabel;
    @FXML private Label              messageLabel;

    @FXML private TableView<SellRow>              myItemTable;
    @FXML private TableColumn<SellRow, Integer>   colId;
    @FXML private TableColumn<SellRow, String>    colName;
    @FXML private TableColumn<SellRow, String>    colType;
    @FXML private TableColumn<SellRow, String>    colStartPrice;
    @FXML private TableColumn<SellRow, String>    colCurrentPrice;
    @FXML private TableColumn<SellRow, String>    colStatus;

    private File   selectedImage;
    private Button submitBtn;
    private final ObservableList<SellRow> myItems = FXCollections.observableArrayList();

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(Item.class, new ItemTypeAdapter())
            .create();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();

        if (user == null || user.getRole() != Role.SELLER) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Không có quyền");
                alert.setHeaderText(null);
                alert.setContentText("Chỉ Seller mới có thể đăng bán sản phẩm.\n"
                        + "Hãy nâng cấp tài khoản tại trang Home.");
                alert.showAndWait();
                try {
                    Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
                    Stage stage = (Stage) nameField.getScene().getWindow();
                    stage.setScene(new Scene(root));
                    stage.show();
                } catch (Exception e) { e.printStackTrace(); }
            });
            return;
        }

        typeCombo.setItems(FXCollections.observableArrayList("POSTER", "FIGURE", "CARD"));

        hoursSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 48, 0));
        hoursSpinner.setEditable(true);

        // Cho phép chọn từ 1 phút trở lên (bỏ giới hạn chỉ 0 và 30 phút)
        minutesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 1));
        minutesSpinner.setEditable(true);

        colId          .setCellValueFactory(new PropertyValueFactory<>("id"));
        colName        .setCellValueFactory(new PropertyValueFactory<>("name"));
        colType        .setCellValueFactory(new PropertyValueFactory<>("type"));
        colStartPrice  .setCellValueFactory(new PropertyValueFactory<>("startPrice"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colStatus      .setCellValueFactory(new PropertyValueFactory<>("status"));
        myItemTable.setItems(myItems);

        ClientSocket.getInstance().addListener(this);
        loadMyItems(user.getId());
    }

    // ── Load danh sách sản phẩm ───────────────────────────────────────────────

    private void loadMyItems(int sellerId) {
        String sessionId = SessionManager.getInstance().getSessionId();
        ClientSocket.getInstance().sendMessage(
                new GetSellerItemsRequest(sessionId, sellerId));
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject json) {
        if (!json.has("action")) return;

        switch (json.get("action").getAsString()) {

            case "SELLER_ITEMS_RESULT" -> {
                GetSellerItemsResponse res = gson.fromJson(json, GetSellerItemsResponse.class);
                Platform.runLater(() -> {
                    if (!res.isSuccess() || res.getItems() == null) return;
                    myItems.clear();
                    for (Item item : res.getItems()) {
                        Auction auction = res.getAuctions() == null ? null :
                                res.getAuctions().stream()
                                .filter(a -> a.getItemId() == item.getId())
                                .findFirst().orElse(null);
                        myItems.add(new SellRow(
                                item.getId(),
                                item.getName(),
                                item.getType().name(),
                                auction != null ? fmt(auction.getStartingPrice()) : "-",
                                auction != null ? fmt(auction.getCurrentPrice())  : "-",
                                auction != null ? auction.getStatus().name()       : "NO AUCTION"
                        ));
                    }
                });
            }

            case "ADD_ITEM_SUCCESS" -> {
                AddItemResponse res = gson.fromJson(json, AddItemResponse.class);
                Platform.runLater(() -> {
                    if (submitBtn != null) submitBtn.setDisable(false);
                    if (res.isSuccess()) {
                        nameField.clear();
                        typeCombo.setValue(null);
                        priceField.clear();
                        uploadLabel.setText("");
                        selectedImage = null;
                        showSuccess("Đăng bán thành công!");
                        User user = SessionManager.getInstance().getCurrentUser();
                        if (user != null) loadMyItems(user.getId());
                    } else {
                        showError(res.getMessage() != null ? res.getMessage() : "Đăng bán thất bại");
                    }
                });
            }
        }
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @FXML
    public void handleUpload(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh sản phẩm");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        selectedImage = fc.showOpenDialog(stage);
        if (selectedImage != null) uploadLabel.setText("✓ " + selectedImage.getName());
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @FXML
    public void handleSell(ActionEvent event) {
        messageLabel.setText("");
        messageLabel.setStyle("");

        String name      = nameField.getText().trim();
        String typeStr   = typeCombo.getValue();
        String priceText = priceField.getText().trim();

        if (name.isEmpty())        { showError("Vui lòng nhập tên sản phẩm"); return; }
        if (typeStr == null)       { showError("Vui lòng chọn loại sản phẩm"); return; }
        if (priceText.isEmpty())   { showError("Vui lòng nhập giá khởi điểm"); return; }
        if (selectedImage == null) { showError("Vui lòng chọn ảnh sản phẩm"); return; }

        BigDecimal startingPrice;
        try {
            startingPrice = new BigDecimal(priceText);
            if (startingPrice.compareTo(BigDecimal.ZERO) <= 0) {
                showError("Giá phải lớn hơn 0"); return;
            }
        } catch (NumberFormatException e) {
            showError("Định dạng giá không hợp lệ"); return;
        }

        hoursSpinner.commitValue();
        minutesSpinner.commitValue();
        int hours        = hoursSpinner.getValue();
        int minutes      = minutesSpinner.getValue();
        int totalMinutes = hours * 60 + minutes;

        // Cho phép tối thiểu 1 phút (bỏ giới hạn 30 phút cũ)
        if (totalMinutes < 1)       { showError("Thời gian tối thiểu là 1 phút"); return; }
        if (totalMinutes > 48 * 60) { showError("Thời gian tối đa là 48 giờ");    return; }

        submitBtn = (Button) event.getSource();
        submitBtn.setDisable(true);

        new Thread(() -> {
            try {
                byte[] imageBytes = FileUtils.toByteArray(selectedImage);
                if (imageBytes == null) {
                    Platform.runLater(() -> {
                        showError("Lỗi đọc file ảnh");
                        submitBtn.setDisable(false);
                    });
                    return;
                }
                String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);

                User          user      = SessionManager.getInstance().getCurrentUser();
                String        sessionId = SessionManager.getInstance().getSessionId();
                ItemType      itemType  = ItemType.valueOf(typeStr);
                LocalDateTime startTime = LocalDateTime.now();
                LocalDateTime endTime   = startTime.plusHours(hours).plusMinutes(minutes);

                AddItemRequest req = new AddItemRequest(
                        name, sessionId, itemType,
                        startingPrice, imageBase64,
                        startTime, endTime, user.getId());

                ClientSocket.getInstance().sendMessage(req);

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showError("Lỗi: " + e.getMessage());
                    submitBtn.setDisable(false);
                });
                e.printStackTrace();
            }
        }).start();
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML
    public void Return(ActionEvent event) throws Exception {
        ClientSocket.getInstance().removeListener(this);
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String msg) {
        messageLabel.setStyle("-fx-text-fill: #ff6b6b;");
        messageLabel.setText(msg);
    }

    private void showSuccess(String msg) {
        messageLabel.setStyle("-fx-text-fill: #00ff88;");
        messageLabel.setText(msg);
    }

    private String fmt(BigDecimal n) {
        if (n == null) return "-";
        return String.format("%,.0f ₫", n);
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class SellRow {
        private final int    id;
        private final String name, type, startPrice, currentPrice, status;

        public SellRow(int id, String name, String type,
                       String startPrice, String currentPrice, String status) {
            this.id           = id;
            this.name         = name;
            this.type         = type;
            this.startPrice   = startPrice;
            this.currentPrice = currentPrice;
            this.status       = status;
        }

        public int    getId()           { return id; }
        public String getName()         { return name; }
        public String getType()         { return type; }
        public String getStartPrice()   { return startPrice; }
        public String getCurrentPrice() { return currentPrice; }
        public String getStatus()       { return status; }
    }
}