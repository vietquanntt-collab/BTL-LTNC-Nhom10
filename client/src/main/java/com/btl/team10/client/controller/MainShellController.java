package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.UiMotion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class MainShellController {

    private static MainShellController instance;

    @FXML private VBox sidebarBox;
    @FXML private StackPane mainContentArea;
    @FXML private VBox guestInfoBox;
    @FXML private VBox sessionPanel;
    @FXML private Label userAccountLabel;
    @FXML private Label userRoleBadge;
    @FXML private Label balanceLabel;
    @FXML private Label frozenLabel;
    @FXML private Button navAuctionsBtn;
    @FXML private Button navSellerBtn;
    @FXML private Button navAdminBtn;
    @FXML private Label shellNoticeLabel;

    @FXML
    public void initialize() {
        instance = this;
        NavigationManager.getInstance().setContentArea(mainContentArea);
        NetworkClient.getInstance().setWalletListener((balance, frozen) ->
                Platform.runLater(() -> {
                    updateWalletLabels(balance, frozen);
                    UiMotion.pulse(balanceLabel, 1.07);
                    UiMotion.pulse(frozenLabel, 1.07);
                }));
        refreshSessionState();
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/login.fxml");
    }

    public static void onSessionUpdated() {
        if (instance != null) {
            Platform.runLater(instance::refreshSessionState);
        }
    }

    public void refreshSessionState() {
        NetworkClient client = NetworkClient.getInstance();
        boolean loggedIn = client.isLoggedIn();

        // Ẩn toàn bộ sidebar khi chưa đăng nhập để màn hình Login bung 100% chiều rộng sân khấu
        if (sidebarBox != null) {
            sidebarBox.setVisible(loggedIn);
            sidebarBox.setManaged(loggedIn);
        }

        guestInfoBox.setVisible(false);
        guestInfoBox.setManaged(false);
        sessionPanel.setVisible(loggedIn);
        sessionPanel.setManaged(loggedIn);
        hideNotice();

        if (!loggedIn) {
            return;
        }

        userAccountLabel.setText("@" + client.account() + " (ID #" + client.userId() + ")");
        String role = client.role() != null ? client.role().toUpperCase() : "BIDDER";
        userRoleBadge.setText(role);

        String badgeColor = switch (role) {
            case "ADMIN" -> "#7e22ce";
            case "SELLER" -> "#c2410c";
            default -> "#2563eb";
        };
        userRoleBadge.setStyle("-fx-background-color: " + badgeColor
                + "; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

        boolean isSeller = "SELLER".equals(role);
        boolean isAdmin = "ADMIN".equals(role);

        navSellerBtn.setVisible(isSeller);
        navSellerBtn.setManaged(isSeller);
        navAdminBtn.setVisible(isAdmin);
        navAdminBtn.setManaged(isAdmin);

        updateWalletLabels(client.balanceCents(), client.frozenCents());
    }

    private void updateWalletLabels(long balanceCents, long frozenCents) {
        long available = Math.max(0L, balanceCents - frozenCents);
        balanceLabel.setText("Khả dụng: " + available + " cents");
        frozenLabel.setText("Đóng băng: " + frozenCents + " cents");
    }

    @FXML
    private void onNavigateAuctions() {
        hideNotice();
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
    }

    @FXML
    private void onNavigateSeller() {
        String path = "/com/btl/team10/client/view/SellerDashboard.fxml";
        if (getClass().getResource(path) == null) {
            showNotice("Màn hình SellerDashboard chưa khả dụng.");
            return;
        }
        hideNotice();
        NavigationManager.getInstance().loadView(path);
    }

    @FXML
    private void onNavigateAdmin() {
        String path = "/com/btl/team10/client/view/AdminDashboard.fxml";
        if (getClass().getResource(path) == null) {
            showNotice("Màn hình AdminDashboard chưa khả dụng.");
            return;
        }
        hideNotice();
        NavigationManager.getInstance().loadView(path);
    }

    @FXML
    private void onLogout() {
        Thread logoutThread = new Thread(() -> {
            NetworkClient.getInstance().logout();
            Platform.runLater(() -> {
                refreshSessionState();
                NavigationManager.getInstance().loadView("/com/btl/team10/client/view/login.fxml");
            });
        }, "auction-logout");
        logoutThread.setDaemon(true);
        logoutThread.start();
    }

    private void showNotice(String text) {
        shellNoticeLabel.setText(text);
        shellNoticeLabel.setVisible(true);
        shellNoticeLabel.setManaged(true);
    }

    private void hideNotice() {
        shellNoticeLabel.setVisible(false);
        shellNoticeLabel.setManaged(false);
    }
}