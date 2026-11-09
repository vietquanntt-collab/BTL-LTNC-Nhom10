package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.state.UIState;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            showError("Tài khoản và mật khẩu không được để trống.");
            return;
        }

        NavigationManager.getInstance().switchState(UIState.LOADING);
        Task<NetworkClient.LoginResult> loginTask = new Task<>() {
            @Override
            protected NetworkClient.LoginResult call() throws Exception {
                return NetworkClient.getInstance().login(username, password);
            }
        };
        loginTask.setOnSucceeded(event ->
                NavigationManager.getInstance()
                        .loadView("/com/btl/team10/client/view/AuctionList.fxml"));
        loginTask.setOnFailed(event -> {
            String message = loginTask.getException() == null
                    ? "Không thể đăng nhập."
                    : loginTask.getException().getMessage();
            LoginController loginController = NavigationManager.getInstance().loadView(
                    "/com/btl/team10/client/view/login.fxml", LoginController.class);
            if (loginController != null) {
                loginController.showError(message);
            }
        });
        Thread thread = new Thread(loginTask, "auction-login");
        thread.setDaemon(true);
        thread.start();
    }

    public void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
