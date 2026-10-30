package com.btl.n10.Controller;

import com.btl.n10.DTO.AuctionSettledResponse;
import com.btl.n10.DTO.DepositRequest;
import com.btl.n10.DTO.DepositResponse;
import com.btl.n10.DTO.GetUserBalanceRequest;
import com.btl.n10.DTO.GetUserBalanceResponse;
import com.btl.n10.DTO.UpgradeSellerRequest;
import com.btl.n10.DTO.UpgradeSellerResponse;
import com.btl.n10.Model.Entity.User;
import com.btl.n10.Model.Enums.Role;
import com.btl.n10.Network.ClientSocket;
import com.btl.n10.Network.ServerResponseListener;
import com.btl.n10.Util.LocalDateTimeAdapter;
import com.btl.n10.Util.UserTypeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

public class HomeController implements ServerResponseListener {

    @FXML private Label balanceLabel;
    @FXML private Label usernameLabel;   // ← THÊM MỚI
    @FXML private Button becomeSellerBtn;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .registerTypeAdapter(User.class, new UserTypeAdapter())
            .create();

    private final Consumer<Object> onBalanceUpdated = e -> refreshBalance();

    @FXML
    public void initialize() {
        if (!SessionManager.getInstance().isLoggedIn()) return;
        ClientSocket.getInstance().addListener(this);
        AppEventBus.subscribe(AppEventBus.BALANCE_UPDATED, onBalanceUpdated);
        refreshBalance();
        refreshUsername();   // ← THÊM MỚI
        updateBecomeSellerVisibility();
    }

    // ── ServerResponseListener ────────────────────────────────────────────────

    @Override
    public void onRespone(JsonObject json) {
        if (!json.has("action")) return;
        switch (json.get("action").getAsString()) {

            case "DEPOSIT_RESULT" -> {
                DepositResponse res = gson.fromJson(json, DepositResponse.class);
                Platform.runLater(() -> {
                    if (res.isSuccess()) {
                        User user = SessionManager.getInstance().getCurrentUser();
                        if (user != null) user.setBalance(res.getNewBalance());
                        refreshBalance();
                        showAlert(Alert.AlertType.INFORMATION, "Thành công",
                                String.format("Nạp thành công!\nSố dư hiện tại: %,.0f ₫",
                                        res.getNewBalance()));
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi",
                                res.getMessage() != null ? res.getMessage() : "Nạp tiền thất bại");
                    }
                });
            }

            case "UPGRADE_SELLER_RESULT" -> {
                UpgradeSellerResponse res = gson.fromJson(json, UpgradeSellerResponse.class);
                Platform.runLater(() -> {
                    if (res.isSuccess() && res.getUpdatedUser() != null) {
                        SessionManager.getInstance().setCurrentUser(res.getUpdatedUser());
                        updateBecomeSellerVisibility();
                        showAlert(Alert.AlertType.INFORMATION, "Thành công",
                                "Tài khoản đã được nâng cấp thành Seller!");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Lỗi",
                                res.getMessage() != null ? res.getMessage()
                                        : "Không thể nâng cấp tài khoản");
                    }
                });
            }

            case "AUCTION_SETTLED" -> {
                AuctionSettledResponse settled = gson.fromJson(json, AuctionSettledResponse.class);
                User me = SessionManager.getInstance().getCurrentUser();
                String sid = SessionManager.getInstance().getSessionId();

                if (settled.isSeller() && settled.getSellerNewBalance() != null && me != null) {
                    Platform.runLater(() -> {
                        me.setBalance(settled.getSellerNewBalance());
                        SessionManager.getInstance().setCurrentUser(me);
                        refreshBalance();
                    });
                } else if (settled.isWinner() && settled.getNewBalance() != null && me != null) {
                    Platform.runLater(() -> {
                        me.setBalance(settled.getNewBalance());
                        SessionManager.getInstance().setCurrentUser(me);
                        refreshBalance();
                    });
                } else if (me != null && sid != null) {
                    ClientSocket.getInstance().sendMessage(
                            new GetUserBalanceRequest(sid, me.getId()));
                }
            }

            case "USER_BALANCE_RESULT" -> {
                GetUserBalanceResponse res = gson.fromJson(json, GetUserBalanceResponse.class);
                if (!res.isSuccess()) return;
                Platform.runLater(() -> {
                    User me = SessionManager.getInstance().getCurrentUser();
                    if (me != null && res.getBalance() != null) {
                        me.setBalance(res.getBalance());
                        me.setFrozenBalance(res.getFrozenBalance());
                        SessionManager.getInstance().setCurrentUser(me);
                    }
                    refreshBalance();
                });
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @FXML
    public void deposit(ActionEvent event) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) return;
        if (user.getRole() != Role.BIDDER && user.getRole() != Role.SELLER) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Admin không thể nạp tiền.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nạp tiền");
        dialog.setHeaderText("Nhập số tiền muốn nạp:");
        dialog.setContentText("Số tiền:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            try {
                BigDecimal amount = new BigDecimal(input.trim().replace(",", ""));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    showAlert(Alert.AlertType.ERROR, "Lỗi", "Số tiền phải lớn hơn 0.");
                    return;
                }
                String sessionId = SessionManager.getInstance().getSessionId();
                ClientSocket.getInstance().sendMessage(
                        new DepositRequest(sessionId, user.getId(), amount));
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Số tiền không hợp lệ.");
            }
        });
    }

    @FXML
    public void becomeSeller(ActionEvent event) {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) return;
        if (user.getRole() != Role.BIDDER) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Bạn đã là Seller hoặc không đủ điều kiện.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Nâng cấp thành Seller?");
        confirm.setContentText("Bạn có muốn chuyển tài khoản từ Bidder sang Seller không?\n(Không thể hoàn tác)");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String sessionId = SessionManager.getInstance().getSessionId();
            ClientSocket.getInstance().sendMessage(
                    new UpgradeSellerRequest(sessionId, user.getId()));
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void Bid(ActionEvent event) throws Exception {
        if (!SessionManager.getInstance().isLoggedIn()) return;
        navigateTo(event, "/fxml/bid.fxml");
    }

    public void Sell(ActionEvent event) throws Exception {
        if (!SessionManager.getInstance().isLoggedIn()) return;
        navigateTo(event, "/fxml/sell.fxml");
    }

    public void account(ActionEvent event) throws Exception {
        if (!SessionManager.getInstance().isLoggedIn()) return;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/account.fxml"));
        Parent root = loader.load();
        Stage popup = new Stage();
        popup.setTitle("My Account");
        popup.setScene(new Scene(root));
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        popup.setResizable(false);
        popup.show();
    }

    public void logout(ActionEvent event) throws Exception {
        ClientSocket.getInstance().removeListener(this);
        AppEventBus.unsubscribe(AppEventBus.BALANCE_UPDATED, onBalanceUpdated);
        AutoBidManager.getInstance().cancelAll();
        SessionManager.getInstance().logout();
        SessionManager.getInstance().setCurrentUser(null);
        SessionManager.getInstance().setSessionId(null);
        ClientSocket.getInstance().close();
        ClientSocket.getInstance().reset();
        navigateTo(event, "/fxml/login.fxml");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshBalance() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (balanceLabel == null || user == null) return;
        BigDecimal bal = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        balanceLabel.setText(String.format("%,.0f", bal));
    }

    // ← THÊM MỚI
    private void refreshUsername() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (usernameLabel == null || user == null) return;
        usernameLabel.setText("Welcome: " + user.getAccount());
    }

    private void updateBecomeSellerVisibility() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (becomeSellerBtn != null)
            becomeSellerBtn.setVisible(user != null && user.getRole() == Role.BIDDER);
    }

    private void navigateTo(ActionEvent event, String fxml) throws Exception {
        ClientSocket.getInstance().removeListener(this);
        AppEventBus.unsubscribe(AppEventBus.BALANCE_UPDATED, onBalanceUpdated);
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}