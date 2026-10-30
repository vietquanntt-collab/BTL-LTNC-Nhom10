package com.btl.n8.Controller;

import com.btl.n8.DTO.BidResponse;
import com.btl.n8.DTO.GetAuctionDetailResponse;
import com.btl.n8.DTO.GetAuctionListResponse;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Service.BidControllerService;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

public class BidController implements ServerResponseListener {

    @FXML private TextField              searchField;
    @FXML private TableView<ItemRow>     itemTable;
    @FXML private TableColumn<ItemRow, String> colId;
    @FXML private TableColumn<ItemRow, String> colName;
    @FXML private TableColumn<ItemRow, String> colType;
    @FXML private TableColumn<ItemRow, String> colPrice;
    @FXML private TableColumn<ItemRow, String> colStatus;
    @FXML private TableColumn<ItemRow, Void>   colAction;

    private final ObservableList<ItemRow> allItems = FXCollections.observableArrayList();

    /** Service xử lý toàn bộ logic — Controller không tự gửi request hay xử lý data */
    private final BidControllerService bidControllerService = new BidControllerService();

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        setupColumns();
        ClientSocket.getInstance().addListener(this);
        loadData();
    }

    public void cleanup() {
        ClientSocket.getInstance().removeListener(this);
    }

    // ── Load data qua socket ──────────────────────────────────────────────────

    private void loadData() {
        String sessionId = SessionManager.getInstance().getSessionId();
        bidControllerService.requestAuctionList(sessionId);
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject response) {
        if (!response.has("action")) return;
        String action = response.get("action").getAsString();

        switch (action) {
            case "AUCTION_LIST_RESULT" -> {
                GetAuctionListResponse res = gson.fromJson(response, GetAuctionListResponse.class);
                if (!res.isSuccess() || res.getRows() == null) return;
                Platform.runLater(() -> {
                    allItems.clear();
                    for (GetAuctionListResponse.AuctionRow r : res.getRows()) {
                        // Tên biến khớp với AuctionDAO.findAllWithItems():
                        // r.itemId, r.itemName, r.itemType, r.currentPrice, r.status, r.auctionId, r.sellerId
                        allItems.add(new ItemRow(
                                r.itemId,
                                r.itemName,
                                r.itemType,
                                r.currentPrice != null
                                        ? String.format("%,.0f ₫", r.currentPrice) : "-",
                                r.status != null ? r.status : "NO AUCTION",
                                r.auctionId,
                                r.sellerId));
                    }
                    itemTable.setItems(allItems);
                });
            }
            case "BID_UPDATE" -> {
                BidResponse res = gson.fromJson(response, BidResponse.class);
                if (!res.isSuccess()) return;
                Platform.runLater(() -> {
                    for (int i = 0; i < allItems.size(); i++) {
                        ItemRow row = allItems.get(i);
                        if (row.getAuctionId() == res.getAuctionId()) {
                            row.setPrice(String.format("%,.0f ₫", res.getCurrentPrice()));
                            row.setStatus("OPEN");
                            allItems.set(i, row);
                            // Force TableView re-render — SimpleStringProperty đôi khi
                            // không trigger refresh tự động khi set cùng row reference
                            itemTable.refresh();
                            break;
                        }
                    }
                });
            }
            case "AUCTION_SETTLED" -> {
                // Reload danh sách khi có phiên kết thúc
                Platform.runLater(this::loadData);
            }
            case "NEW_AUCTION_AVAILABLE" -> {
                // Có seller vừa đăng item mới → reload danh sách để hiện auction mới
                Platform.runLater(this::loadData);
            }
        }
    }

    // ── Column setup ──────────────────────────────────────────────────────────

    private void setupColumns() {
        colId    .setCellValueFactory(c -> c.getValue().idProperty());
        colName  .setCellValueFactory(c -> c.getValue().nameProperty());
        colType  .setCellValueFactory(c -> c.getValue().typeProperty());
        colPrice .setCellValueFactory(c -> c.getValue().priceProperty());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());

        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Bid");
            {
                btn.setOnAction(e -> {
                    ItemRow row = getTableRow().getItem();
                    if (row == null) return;
                    btn.setDisable(true);
                    openBidPopup(row, btn);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                ItemRow row = getTableRow().getItem();
                if (row == null) { setGraphic(null); return; }

                int     uid    = SessionManager.getInstance().getCurrentUser().getId();
                boolean isOwn  = bidControllerService.isOwnItem(row.getSellerId(), uid);
                boolean isOpen = bidControllerService.isAuctionOpen(row.getAuctionId(), row.getStatus());

                btn.setDisable(!isOpen || isOwn);
                btn.setText(isOwn ? "Của bạn" : "Bid");
                btn.setStyle(isOwn ? "-fx-opacity: 0.5;" : "");
                setGraphic(btn);
            }
        });
    }

    // ── Bid popup ─────────────────────────────────────────────────────────────

    private void openBidPopup(ItemRow row, Button btn) {
        // Lấy item detail qua service trước khi mở popup
        String sessionId = SessionManager.getInstance().getSessionId();
        bidControllerService.requestAuctionDetail(sessionId, row.getAuctionId());

        // Đăng ký listener một lần để nhận detail rồi mở popup
        ServerResponseListener detailListener = new ServerResponseListener() {
            @Override
            public void onRespone(JsonObject json) {
                if (!json.has("action")) return;
                if (!"AUCTION_DETAIL_RESULT".equals(json.get("action").getAsString())) return;
                ClientSocket.getInstance().removeListener(this);

                GetAuctionDetailResponse res =
                        gson.fromJson(json, GetAuctionDetailResponse.class);

                Platform.runLater(() -> {
                    if (!res.isSuccess() || res.getAuction() == null) {
                        showError("Không tải được thông tin phiên đấu giá");
                        btn.setDisable(false);
                        return;
                    }
                    try {
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/fxml/bidDetails.fxml"));
                        Parent root = loader.load();

                        BidDetailController ctrl = loader.getController();
                        Stage popup = new Stage();
                        popup.initStyle(StageStyle.UNDECORATED);
                        popup.setTitle("Bid - " + row.getName());
                        popup.setScene(new Scene(root));
                        popup.initModality(Modality.APPLICATION_MODAL);
                        popup.setResizable(false);

                        popup.setOnHidden(e -> {
                            ctrl.cleanup();
                            btn.setDisable(false);
                            loadData();   // Reload nhẹ sau khi đóng popup
                        });

                        ctrl.initData(row.getAuctionId(), res.getAuction());
                        popup.show();

                    } catch (Exception ex) {
                        showError("Lỗi mở cửa sổ: " + ex.getMessage());
                        btn.setDisable(false);
                    }
                });
            }
        };
        ClientSocket.getInstance().addListener(detailListener);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @FXML
    public void handleSearch(ActionEvent event) {
        String query = searchField.getText().trim();
        if (query.isEmpty()) { itemTable.setItems(allItems); return; }

        ObservableList<ItemRow> filtered = FXCollections.observableArrayList();
        try {
            int id = Integer.parseInt(query);
            filtered.addAll(allItems.stream()
                    .filter(r -> r.getId() == id).collect(Collectors.toList()));
        } catch (NumberFormatException e) {
            filtered.addAll(allItems.stream()
                    .filter(r -> r.getName().toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList()));
        }
        itemTable.setItems(filtered);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML
    public void Return(ActionEvent event) throws Exception {
        cleanup();
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /**
     * Row model dùng cho TableView trong BidController.
     * Lưu ý: class này chỉ dành cho UI — không dùng làm DTO truyền qua mạng.
     * DTO truyền qua mạng dùng {@link com.btl.n8.DTO.ItemAuctionRow}.
     */
    public static class ItemRow {
        private final int id, auctionId, sellerId;
        private final SimpleStringProperty idProp, name, type, price, status;

        public ItemRow(int id, String name, String type,
                       String price, String status, int auctionId, int sellerId) {
            this.id        = id;
            this.auctionId = auctionId;
            this.sellerId  = sellerId;
            this.idProp    = new SimpleStringProperty(String.valueOf(id));
            this.name      = new SimpleStringProperty(name);
            this.type      = new SimpleStringProperty(type);
            this.price     = new SimpleStringProperty(price);
            this.status    = new SimpleStringProperty(status);
        }

        public SimpleStringProperty idProperty()     { return idProp; }
        public SimpleStringProperty nameProperty()   { return name; }
        public SimpleStringProperty typeProperty()   { return type; }
        public SimpleStringProperty priceProperty()  { return price; }
        public SimpleStringProperty statusProperty() { return status; }

        public int    getId()        { return id; }
        public int    getAuctionId() { return auctionId; }
        public int    getSellerId()  { return sellerId; }
        public String getName()      { return name.get(); }
        public String getStatus()    { return status.get(); }

        public void setPrice(String v)  { price.set(v); }
        public void setStatus(String v) { status.set(v); }
    }

    /**
     * Giữ lại inner class này để tương thích ngược với AuctionDAOImpl cũ (nếu vẫn còn build cũ).
     * Sau khi đã migrate AuctionDAO sang dùng {@link com.btl.n8.DTO.ItemAuctionRow},
     * class này có thể xóa đi.
     *
     * @deprecated Dùng {@link com.btl.n8.DTO.ItemAuctionRow} thay thế.
     */
    @Deprecated
    public static class ItemAuctionRow {
        public int        itemId;
        public String     itemName;
        public String     itemType;
        public java.math.BigDecimal currentPrice;
        public String     status;
        public int        auctionId;
        public int        sellerId;

        public ItemAuctionRow() {}

        public ItemAuctionRow(int itemId, String itemName, String itemType,
                              java.math.BigDecimal currentPrice, String status,
                              int auctionId, int sellerId) {
            this.itemId       = itemId;
            this.itemName     = itemName;
            this.itemType     = itemType;
            this.currentPrice = currentPrice;
            this.status       = status;
            this.auctionId    = auctionId;
            this.sellerId     = sellerId;
        }
    }
}