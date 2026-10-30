package com.btl.n8.Controller;

import com.btl.n8.DTO.GetUserBidsRequest;
import com.btl.n8.DTO.GetUserBidsResponse;
import com.btl.n8.Model.Entity.Bid;
import com.btl.n8.Model.Entity.User;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class AccounthomeController implements ServerResponseListener {

    @FXML private Label avatarLabel;
    @FXML private Label usernameLabel;
    @FXML private Label usernameLabel2;
    @FXML private Label idLabel;
    @FXML private Label roleLabel;
    @FXML private Label roleLabel2;

    @FXML private TableView<BidRow>           bidTable;
    @FXML private TableColumn<BidRow, String> colItem;
    @FXML private TableColumn<BidRow, String> colAmount;
    @FXML private TableColumn<BidRow, String> colTime;
    @FXML private TableColumn<BidRow, String> colStatus;

    private final ObservableList<BidRow> bidRows = FXCollections.observableArrayList();

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) { usernameLabel.setText("Not logged in"); return; }

        String account  = user.getAccount();
        String initials = account.length() >= 2
                ? account.substring(0, 2).toUpperCase() : account.toUpperCase();
        avatarLabel.setText(initials);
        usernameLabel.setText(account);
        usernameLabel2.setText(account);
        idLabel.setText("#" + user.getId());
        roleLabel.setText(user.getRole().name());
        roleLabel2.setText(user.getRole().name());

        switch (user.getRole()) {
            case BIDDER -> roleLabel.setStyle(
                    "-fx-background-color:#1a3a5c;-fx-text-fill:#7ecfff;" +
                            "-fx-padding:3 10 3 10;-fx-background-radius:99;");
            case SELLER -> roleLabel.setStyle(
                    "-fx-background-color:#3a2800;-fx-text-fill:#ffd700;" +
                            "-fx-padding:3 10 3 10;-fx-background-radius:99;");
            case ADMIN  -> roleLabel.setStyle(
                    "-fx-background-color:#2a003a;-fx-text-fill:#cc88ff;" +
                            "-fx-padding:3 10 3 10;-fx-background-radius:99;");
        }

        colItem  .setCellValueFactory(new PropertyValueFactory<>("item"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colTime  .setCellValueFactory(new PropertyValueFactory<>("time"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        bidTable.setItems(bidRows);

        // Đăng ký listener rồi gửi request qua socket
        ClientSocket.getInstance().addListener(this);
        String sessionId = SessionManager.getInstance().getSessionId();
        ClientSocket.getInstance().sendMessage(
                new GetUserBidsRequest(sessionId, user.getId()));
    }

    @Override
    public void onRespone(JsonObject json) {
        if (!json.has("action")) return;
        if (!"USER_BIDS_RESULT".equals(json.get("action").getAsString())) return;

        GetUserBidsResponse res = gson.fromJson(json, GetUserBidsResponse.class);
        ClientSocket.getInstance().removeListener(this);

        Platform.runLater(() -> {
            if (!res.isSuccess() || res.getBids() == null) return;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm dd/MM");
            bidRows.clear();
            for (Bid bid : res.getBids()) {
                String time   = bid.getBidTime() != null ? bid.getBidTime().format(fmt) : "-";
                String amount = String.format("%,.0f ₫", bid.getAmount());
                bidRows.add(new BidRow("#" + bid.getAuctionId(), amount,
                        time, bid.getStatus().name()));
            }
            bidTable.setItems(bidRows);
        });
    }

    @FXML
    public void handleClose() {
        ClientSocket.getInstance().removeListener(this);
        Stage stage = (Stage) avatarLabel.getScene().getWindow();
        stage.close();
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class BidRow {
        private final String item, amount, time, status;

        public BidRow(String item, String amount, String time, String status) {
            this.item = item; this.amount = amount;
            this.time = time; this.status = status;
        }

        public String getItem()   { return item; }
        public String getAmount() { return amount; }
        public String getTime()   { return time; }
        public String getStatus() { return status; }
    }
}