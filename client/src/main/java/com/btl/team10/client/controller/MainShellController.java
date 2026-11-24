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

import java.util.Locale;

public class MainShellController {

    private static final String NAV_ACTIVE_STYLE =
            "-fx-background-color: rgba(250,204,21,0.12); -fx-text-fill: #fde047; -fx-border-color: #facc15; -fx-border-width: 0 0 0 3; -fx-background-radius: 7; -fx-padding: 0 12; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String NAV_INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-background-radius: 7; -fx-padding: 0 12; -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand;";

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
                    UiMotion.pulse(balanceLabel, 1.06);
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

        userAccountLabel.setText("@" + client.account());
        String role = client.role() != null ? client.role().toUpperCase() : "BIDDER";
        userRoleBadge.setText(role);

        String badgeStyle = switch (role) {
            case "ADMIN" -> "-fx-background-color: rgba(192,132,252,0.16); -fx-text-fill: #d8b4fe;";
            case "SELLER" -> "-fx-background-color: rgba(251,146,60,0.16); -fx-text-fill: #fdba74;";
            default -> "-fx-background-color: rgba(56,189,248,0.16); -fx-text-fill: #7dd3fc;";
        };
        userRoleBadge.setStyle(badgeStyle
                + " -fx-padding: 2 7; -fx-background-radius: 6; -fx-font-size: 10px; -fx-font-weight: bold;");

        boolean isSeller = "SELLER".equals(role);
        boolean isAdmin = "ADMIN".equals(role);

        navSellerBtn.setVisible(isSeller);
        navSellerBtn.setManaged(isSeller);
        navAdminBtn.setVisible(isAdmin);
        navAdminBtn.setManaged(isAdmin);

        setActiveNav(navAuctionsBtn);
        updateWalletLabels(client.balanceCents(), client.frozenCents());
    }

    private void setActiveNav(Button activeBtn) {
        if (navAuctionsBtn != null) navAuctionsBtn.setStyle(activeBtn == navAuctionsBtn ? NAV_ACTIVE_STYLE : NAV_INACTIVE_STYLE);
        if (navSellerBtn != null) navSellerBtn.setStyle(activeBtn == navSellerBtn ? NAV_ACTIVE_STYLE : NAV_INACTIVE_STYLE);
        if (navAdminBtn != null) navAdminBtn.setStyle(activeBtn == navAdminBtn ? NAV_ACTIVE_STYLE : NAV_INACTIVE_STYLE);
    }

    private void updateWalletLabels(long balanceCents, long frozenCents) {
        long available = Math.max(0L, balanceCents - frozenCents);
        balanceLabel.setText(String.format(Locale.US, "%,d c", available));
        frozenLabel.setText(String.format(Locale.US, "Đóng băng: %,d c", frozenCents));
    }

    @FXML
    private void onNavigateAuctions() {
        hideNotice();
        setActiveNav(navAuctionsBtn);
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
        setActiveNav(navSellerBtn);
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
        setActiveNav(navAdminBtn);
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