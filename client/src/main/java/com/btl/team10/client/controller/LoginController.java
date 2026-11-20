package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {
    @FXML private Label formTitleLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button submitButton;
    @FXML private Button toggleModeButton;

    private boolean registerMode = false;

    @FXML
    private void handleToggleMode() {
        registerMode = !registerMode;
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();

        if (registerMode) {
            formTitleLabel.setText("ĐĂNG KÝ TÀI KHOẢN MỚI");
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
            submitButton.setText("Đăng ký tài khoản");
            submitButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            toggleModeButton.setText("Đã có tài khoản? Quay lại đăng nhập");
        } else {
            formTitleLabel.setText("ĐĂNG NHẬP HỆ THỐNG");
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            submitButton.setText("Đăng nhập");
            submitButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
            toggleModeButton.setText("Chưa có tài khoản? Đăng ký ngay");
        }
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
        submitButton.setText("Đang đăng nhập...");
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
                    submitButton.setText("Đăng nhập");
                    showError(e.getMessage() != null ? e.getMessage() : "Không thể đăng nhập.");
                });
            }
        }, "auction-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void executeRegister(String username, String password) {
        setControlsDisabled(true);
        submitButton.setText("Đang đăng ký...");
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().register(username, password);
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    handleToggleMode();
                    usernameField.setText(username);
                    showSuccess("Đăng ký thành công! Vui lòng đăng nhập.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    submitButton.setText("Đăng ký tài khoản");
                    showError(e.getMessage() != null ? e.getMessage() : "Đăng ký thất bại.");
                });
            }
        }, "auction-register");
        thread.setDaemon(true);
        thread.start();
    }

    private void setControlsDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled);
        submitButton.setDisable(disabled);
        toggleModeButton.setDisable(disabled);
    }

    public void showError(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void showSuccess(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}