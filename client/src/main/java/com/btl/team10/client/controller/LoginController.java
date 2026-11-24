package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;

/** Authentication form. Requests run outside the JavaFX thread. */
public final class LoginController {
    @FXML private Pane bgPane;
    @FXML private ImageView loginBgView;
    @FXML private VBox authCardBox;
    @FXML private Button loginTabBtn, registerTabBtn, submitButton, toggleModeButton;
    @FXML private Label formTitleLabel, formSubtitleLabel, feedbackLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField, confirmPasswordField;
    @FXML private VBox confirmGroup;
    private boolean registerMode;
    private boolean disposed;

    @FXML private void initialize() {
        Image backdrop = StageAssetLoader.loadRawBackdrop("login_bg");
        loginBgView.setImage(backdrop);
        loginBgView.fitWidthProperty().bind(bgPane.widthProperty());
        loginBgView.fitHeightProperty().bind(bgPane.heightProperty());
        Runnable crop = () -> {
            if (backdrop == null || bgPane.getWidth() <= 0 || bgPane.getHeight() <= 0) return;
            double scale = Math.max(bgPane.getWidth() / backdrop.getWidth(), bgPane.getHeight() / backdrop.getHeight());
            double w = bgPane.getWidth() / scale, h = bgPane.getHeight() / scale;
            loginBgView.setViewport(new Rectangle2D((backdrop.getWidth()-w)/2, (backdrop.getHeight()-h)/2, w, h));
        };
        bgPane.widthProperty().addListener((o,a,b) -> crop.run());
        bgPane.heightProperty().addListener((o,a,b) -> crop.run());
        applyModeUi(false);
    }
    @FXML private void switchToLogin() { if (registerMode) applyModeUi(false); }
    @FXML private void switchToRegister() { if (!registerMode) applyModeUi(true); }
    @FXML private void handleToggleMode() { applyModeUi(!registerMode); }
    private void applyModeUi(boolean register) {
        registerMode = register;
        hideFeedback(); passwordField.clear(); confirmPasswordField.clear();
        loginTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), !register);
        registerTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), register);
        formTitleLabel.setText(register ? "Tạo tài khoản mới" : "Chào mừng trở lại");
        formSubtitleLabel.setText(register ? "Bắt đầu hành trình sưu tầm của bạn." : "Đăng nhập để tham gia phiên đấu giá.");
        confirmGroup.setVisible(register); confirmGroup.setManaged(register);
        submitButton.setText(register ? "Tạo tài khoản" : "Đăng nhập");
        toggleModeButton.setText(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký ngay");
    }
    @FXML private void handleSubmit() {
        String account = usernameField.getText().trim(), password = passwordField.getText();
        if (account.isBlank() || password.isBlank()) { showError("Vui lòng nhập tài khoản và mật khẩu."); return; }
        if (registerMode && (account.length() < 3 || password.length() < 6)) {
            showError("Tên tài khoản cần ít nhất 3 ký tự; mật khẩu cần ít nhất 6 ký tự."); return;
        }
        if (registerMode && !password.equals(confirmPasswordField.getText())) {
            showError("Mật khẩu xác nhận chưa khớp."); return;
        }
        boolean registering = registerMode;
        setControlsDisabled(true); submitButton.setText(registering ? "Đang tạo tài khoản…" : "Đang đăng nhập…");
        Thread worker = new Thread(() -> {
            try {
                if (registering) NetworkClient.getInstance().register(account, password);
                else NetworkClient.getInstance().login(account, password);
                Platform.runLater(() -> {
                    if (disposed) return;
                    if (registering) {
                        setControlsDisabled(false); applyModeUi(false); usernameField.setText(account);
                        showFeedback("Đăng ký thành công. Nhập mật khẩu để đăng nhập.", false);
                    } else {
                        MainShellController.onSessionUpdated();
                        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                    }
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (disposed) return;
                    setControlsDisabled(false); submitButton.setText(registering ? "Tạo tài khoản" : "Đăng nhập");
                    showError(error.getMessage() == null ? "Không thể kết nối. Kiểm tra server và thử lại." : error.getMessage());
                });
            }
        }, "auction-auth");
        worker.setDaemon(true); worker.start();
    }
    private void setControlsDisabled(boolean disabled) {
        loginTabBtn.setDisable(disabled); registerTabBtn.setDisable(disabled);
        usernameField.setDisable(disabled); passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled); submitButton.setDisable(disabled);
        toggleModeButton.setDisable(disabled);
    }
    public void showError(String message) { showFeedback(message, true); }
    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(error ? "-fx-background-color: #421f2b; -fx-text-fill: #fecaca; -fx-padding: 10 12; -fx-background-radius: 8;"
                : "-fx-background-color: #173e35; -fx-text-fill: #bbf7d0; -fx-padding: 10 12; -fx-background-radius: 8;");
        feedbackLabel.setVisible(true); feedbackLabel.setManaged(true);
    }
    private void hideFeedback() { feedbackLabel.setVisible(false); feedbackLabel.setManaged(false); }
    public void cleanup() { disposed = true; }
}
