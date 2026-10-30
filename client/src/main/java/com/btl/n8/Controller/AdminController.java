package com.btl.n8.Controller;

import com.btl.n8.DTO.AdminRequest;
import com.btl.n8.DTO.AdminResponse;
import com.btl.n8.Model.Entity.Auction;
import com.btl.n8.Model.Entity.Item;
import com.btl.n8.Model.Entity.User;
import com.btl.n8.Model.Enums.AuctionStatus;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.ItemTypeAdapter;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.btl.n8.Util.UserTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.util.Optional;

public class AdminController implements ServerResponseListener {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Button   navDashboard, navUsers, navAuctions, navItems;
    @FXML private StackPane contentArea;
    @FXML private VBox     panelDashboard, panelUsers, panelAuctions, panelItems;

    @FXML private Label lblTotalUsers, lblTotalAuctions, lblOpenAuctions;
    @FXML private Label lblTotalItems, lblTotalSellers, lblCancelledAuctions;
    @FXML private Label lblAdminAccount;

    @FXML private TableView<User>    tableUsers;
    @FXML private TableColumn<User, String> colUserId, colUserAccount, colUserRole,
            colUserBalance, colUserActions;

    @FXML private TableView<Auction> tableAuctions;
    @FXML private TableColumn<Auction, String> colAuctionId, colAuctionItemId,
            colAuctionStartPrice, colAuctionCurrentPrice,
            colAuctionStatus, colAuctionEndTime, colAuctionActions;

    @FXML private TableView<Item>    tableItems;
    @FXML private TableColumn<Item, String> colItemId, colItemName,
            colItemType, colItemSeller, colItemActions;

    private String pendingAction = null;

    // FIX: thêm UserTypeAdapter để deserialize List<User> trong AdminResponse (abstract User)
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(User.class, new UserTypeAdapter())
            .registerTypeAdapter(Item.class, new ItemTypeAdapter())  // THÊM DÒNG NÀY
            .create();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        User admin = SessionManager.getInstance().getCurrentUser();
        if (admin != null && lblAdminAccount != null)
            lblAdminAccount.setText(admin.getAccount());

        ClientSocket.getInstance().addListener(this);
        setupUserTable();
        setupAuctionTable();
        setupItemTable();
        showPanel("dashboard");
        sendAdmin("ADMIN_GET_DASHBOARD", 0);
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject json) {
        if (!json.has("action")) return;
        if (!"ADMIN_RESULT".equals(json.get("action").getAsString())) return;

        // FIX: gson đã có UserTypeAdapter → AdminResponse.users (List<User>) deserialize đúng
        AdminResponse res = gson.fromJson(json, AdminResponse.class);
        Platform.runLater(() -> {
            if (!res.isSuccess()) {
                showAlert(Alert.AlertType.ERROR, res.getMessage());
                return;
            }
            // Dashboard
            if (res.getTotalUsers() > 0 || res.getTotalAuctions() > 0 ||
                    res.getTotalItems() > 0) {
                lblTotalUsers.setText(String.valueOf(res.getTotalUsers()));
                lblTotalAuctions.setText(String.valueOf(res.getTotalAuctions()));
                lblOpenAuctions.setText(String.valueOf(res.getOpenAuctions()));
                lblTotalItems.setText(String.valueOf(res.getTotalItems()));
                lblTotalSellers.setText(String.valueOf(res.getTotalSellers()));
                lblCancelledAuctions.setText(String.valueOf(res.getCancelledAuctions()));
            }
            // Users
            if (res.getUsers() != null) {
                tableUsers.setItems(FXCollections.observableArrayList(res.getUsers()));
            }
            // Auctions
            if (res.getAuctions() != null) {
                tableAuctions.setItems(FXCollections.observableArrayList(res.getAuctions()));
            }
            // Items
            if (res.getItems() != null) {
                tableItems.setItems(FXCollections.observableArrayList(res.getItems()));
            }
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML public void onNavDashboard(ActionEvent e) {
        showPanel("dashboard");
        sendAdmin("ADMIN_GET_DASHBOARD", 0);
    }
    @FXML public void onNavUsers(ActionEvent e) {
        showPanel("users");
        sendAdmin("ADMIN_GET_USERS", 0);
    }
    @FXML public void onNavAuctions(ActionEvent e) {
        showPanel("auctions");
        sendAdmin("ADMIN_GET_AUCTIONS", 0);
    }
    @FXML public void onNavItems(ActionEvent e) {
        showPanel("items");
        sendAdmin("ADMIN_GET_ITEMS", 0);
    }

    @FXML
    public void onLogout(ActionEvent event) throws Exception {
        ClientSocket.getInstance().removeListener(this);
        SessionManager.getInstance().logout();
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    // ── Table setups ──────────────────────────────────────────────────────────

    private void setupUserTable() {
        colUserId     .setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getId())));
        colUserAccount.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getAccount()));
        colUserRole   .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getRole().name()));
        colUserBalance.setCellValueFactory(d -> {
            var b = d.getValue().getBalance();
            return new SimpleStringProperty(b != null ? b.toPlainString() : "0");
        });
        colUserActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnUp  = new Button("↑ Seller");
            private final Button btnDn  = new Button("↓ Bidder");
            private final Button btnDel = new Button("Xóa");
            {
                btnUp .setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    if (confirm("Nâng cấp " + u.getAccount() + " lên Seller?")) {
                        sendAdmin("ADMIN_UPGRADE_USER", u.getId());
                        sendAdmin("ADMIN_GET_USERS", 0);
                    }
                });
                btnDn .setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    if (confirm("Hạ cấp " + u.getAccount() + " về Bidder?")) {
                        sendAdmin("ADMIN_DEMOTE_USER", u.getId());
                        sendAdmin("ADMIN_GET_USERS", 0);
                    }
                });
                btnDel.setOnAction(e -> {
                    User u = getTableView().getItems().get(getIndex());
                    if (confirm("Xóa tài khoản " + u.getAccount() + "?")) {
                        sendAdmin("ADMIN_DELETE_USER", u.getId());
                        sendAdmin("ADMIN_GET_USERS", 0);
                    }
                });
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty ? null :
                        new javafx.scene.layout.HBox(4, btnUp, btnDn, btnDel));
            }
        });
    }

    private void setupAuctionTable() {
        colAuctionId          .setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getId())));
        colAuctionItemId      .setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getItemId())));
        colAuctionStartPrice  .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStartingPrice().toPlainString()));
        colAuctionCurrentPrice.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCurrentPrice().toPlainString()));
        colAuctionStatus      .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStatus().name()));
        colAuctionEndTime     .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getEndTime().toString()));

        colAuctionActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnClose  = new Button("Đóng");
            private final Button btnCancel = new Button("Hủy");
            private final Button btnDel    = new Button("Xóa");
            {
                btnClose .setOnAction(e -> {
                    Auction a = getTableView().getItems().get(getIndex());
                    if (a.getStatus() != AuctionStatus.OPEN) {
                        showAlert(Alert.AlertType.WARNING, "Phiên không đang OPEN."); return;
                    }
                    if (confirm("Đóng phiên #" + a.getId() + "?")) {
                        sendAdmin("ADMIN_CLOSE_AUCTION", a.getId());
                        sendAdmin("ADMIN_GET_AUCTIONS", 0);
                    }
                });
                btnCancel.setOnAction(e -> {
                    Auction a = getTableView().getItems().get(getIndex());
                    if (confirm("Hủy phiên #" + a.getId() + "?")) {
                        sendAdmin("ADMIN_CANCEL_AUCTION", a.getId());
                        sendAdmin("ADMIN_GET_AUCTIONS", 0);
                    }
                });
                btnDel   .setOnAction(e -> {
                    Auction a = getTableView().getItems().get(getIndex());
                    if (confirm("Xóa vĩnh viễn phiên #" + a.getId() + "?")) {
                        sendAdmin("ADMIN_DELETE_AUCTION", a.getId());
                        sendAdmin("ADMIN_GET_AUCTIONS", 0);
                    }
                });
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty) { setGraphic(null); return; }
                Auction a = getTableView().getItems().get(getIndex());
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(4);
                if (a.getStatus() == AuctionStatus.OPEN)
                    box.getChildren().addAll(btnClose, btnCancel);
                box.getChildren().add(btnDel);
                setGraphic(box);
            }
        });
    }

    private void setupItemTable() {
        colItemId    .setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getId())));
        colItemName  .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getName()));
        colItemType  .setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getType().name()));
        colItemSeller.setCellValueFactory(d -> new SimpleStringProperty(
                String.valueOf(d.getValue().getSellerId())));

        colItemActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDel = new Button("Xóa");
            {
                btnDel.setOnAction(e -> {
                    Item item = getTableView().getItems().get(getIndex());
                    if (confirm("Xóa vật phẩm \"" + item.getName() + "\"?")) {
                        sendAdmin("ADMIN_DELETE_ITEM", item.getId());
                        sendAdmin("ADMIN_GET_ITEMS", 0);
                    }
                });
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setGraphic(empty ? null : btnDel);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendAdmin(String action, int targetId) {
        String sessionId = SessionManager.getInstance().getSessionId();
        ClientSocket.getInstance().sendMessage(
                new AdminRequest(action, sessionId, targetId));
    }

    private void showPanel(String name) {
        panelDashboard.setVisible(false); panelDashboard.setManaged(false);
        panelUsers    .setVisible(false); panelUsers    .setManaged(false);
        panelAuctions .setVisible(false); panelAuctions .setManaged(false);
        panelItems    .setVisible(false); panelItems    .setManaged(false);
        switch (name) {
            case "dashboard" -> { panelDashboard.setVisible(true); panelDashboard.setManaged(true); }
            case "users"     -> { panelUsers    .setVisible(true); panelUsers    .setManaged(true); }
            case "auctions"  -> { panelAuctions .setVisible(true); panelAuctions .setManaged(true); }
            case "items"     -> { panelItems    .setVisible(true); panelItems    .setManaged(true); }
        }
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("Xác nhận");
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showAlert(Alert.AlertType type, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
}
