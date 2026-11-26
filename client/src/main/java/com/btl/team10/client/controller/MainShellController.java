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
    @FXML private Label connectionLabel;

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
        NetworkClient.getInstance().addConnectionListener(connected -> Platform.runLater(() -> {
            connectionLabel.setText(connected ? "Đã kết nối" : "Đang kết nối lại…");
            if (connected && !NetworkClient.getInstance().isLoggedIn()) {
                refreshSessionState();
                NavigationManager.getInstance().loadView("/com/btl/team10/client/view/login.fxml");
            }
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

        userAccountLabel.setText("@" + client.account());
        String role = client.role() != null ? client.role().toUpperCase() : "BIDDER";
        userRoleBadge.setText(role);

        String badgeStyle = switch (role) {
            case "ADMIN" -> "-fx-background-color: #302443; -fx-text-fill: #d8b4fe;";
            case "SELLER" -> "-fx-background-color: #392c26; -fx-text-fill: #fdba74;";
            default -> "-fx-background-color: #193447; -fx-text-fill: #7dd3fc;";
        };
        userRoleBadge.setStyle(badgeStyle
                + " -fx-padding: 2 7; -fx-background-radius: 6; -fx-font-size: 10px; -fx-font-weight: bold;");

        boolean isSeller = "SELLER".equals(role);
        boolean isAdmin = "ADMIN".equals(role);

        navSellerBtn.setVisible(isSeller);
        navSellerBtn.setManaged(isSeller);
        navAdminBtn.setVisible(isAdmin);
        navAdminBtn.setManaged(isAdmin);

        updateWalletLabels(client.balanceCents(), client.frozenCents());
        connectionLabel.setText(client.isConnected() ? "Đã kết nối" : "Đang kết nối lại…");
        selectNav(navAuctionsBtn);
    }

    private void updateWalletLabels(long balanceCents, long frozenCents) {
        long available = Math.max(0L, balanceCents - frozenCents);
        balanceLabel.setText(String.format(java.util.Locale.US, "%,d cents", available));
        frozenLabel.setText(String.format(java.util.Locale.US, "Đang giữ: %,d cents", frozenCents));
    }

    @FXML
    private void onNavigateAuctions() {
        selectNav(navAuctionsBtn);
        hideNotice();
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
    }

    @FXML
    private void onNavigateSeller() {
        selectNav(navSellerBtn);
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
        selectNav(navAdminBtn);
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

    private void selectNav(Button selected) {
        for (Button button : new Button[]{navAuctionsBtn, navSellerBtn, navAdminBtn}) {
            boolean active = button == selected;
            button.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("active"), active);
            button.setStyle("-fx-background-color: " + (active ? "rgba(244,114,182,0.24)" : "transparent")
                    + "; -fx-text-fill: " + (active ? "#ffffff" : "#f9a8d4")
                    + "; -fx-border-color: " + (active ? "#f472b6" : "transparent")
                    + "; -fx-border-width: 0 0 0 3; -fx-background-radius: 8; -fx-padding: 0 12;"
                    + " -fx-font-size: 12.5px; -fx-font-weight: bold; -fx-cursor: hand;");
        }
    }
}
