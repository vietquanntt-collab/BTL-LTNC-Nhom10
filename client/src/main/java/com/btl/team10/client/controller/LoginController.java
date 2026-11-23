package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public final class LoginController {

    private static final String TAB_ACTIVE_STYLE =
            "-fx-background-color: white; -fx-text-fill: #1e293b; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String TAB_INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";

    @FXML private Pane bgPane;
    @FXML private ImageView loginBgView;

    @FXML private Button loginTabBtn;
    @FXML private Button registerTabBtn;
    @FXML private Label formTitleLabel;
    @FXML private Label formSubtitleLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmGroup;
    @FXML private PasswordField confirmPasswordField;
    @FXML private VBox demoQuickBox;
    @FXML private Button submitButton;
    @FXML private Button toggleModeButton;

    private boolean registerMode = false;

    @FXML
    private void initialize() {
        setupBackgroundImage();
        applyModeUi(false);
    }

    private void setupBackgroundImage() {
        Image bgImage = StageAssetLoader.loadRawBackdrop("login_bg");
        if (bgImage != null && loginBgView != null && bgPane != null) {
            loginBgView.setImage(bgImage);
            loginBgView.fitWidthProperty().bind(bgPane.widthProperty());
            loginBgView.fitHeightProperty().bind(bgPane.heightProperty());
        }
    }

    @FXML
    private void switchToLogin() {
        if (registerMode) {
            applyModeUi(false);
        }
    }

    @FXML
    private void switchToRegister() {
        if (!registerMode) {
            applyModeUi(true);
        }
    }

    @FXML
    private void handleToggleMode() {
        applyModeUi(!registerMode);
    }

    private void applyModeUi(boolean toRegister) {
        registerMode = toRegister;
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();

        if (registerMode) {
            loginTabBtn.setStyle(TAB_INACTIVE_STYLE);
            registerTabBtn.setStyle(TAB_ACTIVE_STYLE);
            formTitleLabel.setText("TẠO TÀI KHOẢN MỚI");
            formSubtitleLabel.setText("Đăng ký tài khoản mới để tham gia đấu giá trực tiếp");
            confirmGroup.setVisible(true);
            confirmGroup.setManaged(true);
            demoQuickBox.setVisible(false);
            demoQuickBox.setManaged(false);
            submitButton.setText("Hoàn tất đăng ký");
            submitButton.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
            toggleModeButton.setText("Đã có tài khoản? Quay lại đăng nhập");
        } else {
            loginTabBtn.setStyle(TAB_ACTIVE_STYLE);
            registerTabBtn.setStyle(TAB_INACTIVE_STYLE);
            formTitleLabel.setText("CHÀO MỪNG TRỞ LẠI");
            formSubtitleLabel.setText("Đăng nhập để theo dõi và tham gia phiên đấu giá");
            confirmGroup.setVisible(false);
            confirmGroup.setManaged(false);
            demoQuickBox.setVisible(true);
            demoQuickBox.setManaged(true);
            submitButton.setText("Đăng nhập vào sàn");
            submitButton.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
            toggleModeButton.setText("Chưa có tài khoản? Đăng ký ngay");
        }
    }

    @FXML
    private void fillDemoBidder() {
        switchToLogin();
        usernameField.setText("bidder1");
        passwordField.setText("bidder123");
        hideFeedback();
    }

    @FXML
    private void fillDemoSeller() {
        switchToLogin();
        usernameField.setText("seller");
        passwordField.setText("seller123");
        hideFeedback();
    }

    @FXML
    private void fillDemoAdmin() {
        switchToLogin();
        usernameField.setText("admin");
        passwordField.setText("admin123");
        hideFeedback();
    }

    @FXML
    private void handleSubmit() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            showError("Tài khoản và mật khẩu không được để trống.");
            return;
        }

        if (registerMode) {
            if (username.length() < 3) {
                showError("Tên đăng nhập phải có ít nhất 3 ký tự.");
                return;
            }
            if (password.length() < 6) {
                showError("Mật khẩu đăng ký phải có ít nhất 6 ký tự.");
                return;
            }
            if (!password.equals(confirmPasswordField.getText())) {
                showError("Mật khẩu xác nhận không khớp.");
                return;
            }
            executeRegister(username, password);
        } else {
            executeLogin(username, password);
        }
    }

    private void executeLogin(String username, String password) {
        setControlsDisabled(true);
        submitButton.setText("Đang kết nối tới sàn...");
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().login(username, password);
                Platform.runLater(() -> {
                    MainShellController.onSessionUpdated();
                    NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    submitButton.setText("Đăng nhập vào sàn");
                    showError(e.getMessage() != null ? e.getMessage() : "Không thể đăng nhập.");
                });
            }
        }, "auction-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void executeRegister(String username, String password) {
        setControlsDisabled(true);
        submitButton.setText("Đang tạo tài khoản...");
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().register(username, password);
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    applyModeUi(false);
                    usernameField.setText(username);
                    passwordField.requestFocus();
                    showSuccess("Đăng ký thành công! Vui lòng nhập mật khẩu để đăng nhập.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    submitButton.setText("Hoàn tất đăng ký");
                    showError(e.getMessage() != null ? e.getMessage() : "Đăng ký thất bại.");
                });
            }
        }, "auction-register");
        thread.setDaemon(true);
        thread.start();
    }

    private void setControlsDisabled(boolean disabled) {
        loginTabBtn.setDisable(disabled);
        registerTabBtn.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled);
        submitButton.setDisable(disabled);
        toggleModeButton.setDisable(disabled);
    }

    public void showError(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #b91c1c; -fx-border-color: #fecaca; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void showSuccess(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #15803d; -fx-border-color: #bbf7d0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}