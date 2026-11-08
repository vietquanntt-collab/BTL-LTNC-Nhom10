package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.state.UIState;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        // 1. Validation (Bắt lỗi ngay trên Client, không cần gọi Server)
        if (username.isEmpty() || password.isEmpty()) {
            showError("Tài khoản và mật khẩu không được để trống!");
            return;
        }

        // 2. Bật UI State: LOADING (chặn người dùng bấm linh tinh)
        NavigationManager.getInstance().switchState(UIState.LOADING);

        // 3. Sử dụng Task để gọi mạng trên một Thread riêng biệt (Tránh làm đơ giao diện JavaFX)
        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                return NetworkClient.getInstance().login(username, password);
            }
        };

        // 4. Xử lý khi có kết quả từ Server
        loginTask.setOnSucceeded(e -> {
            boolean isSuccess = loginTask.getValue();
            if (isSuccess) {
                // TODO: Tuần sau sẽ load file AuctionList.fxml vào đây. Tạm thời hiển thị EMPTY.
                NavigationManager.getInstance().switchState(UIState.EMPTY);
            } else {
                // Nếu sai pass: Load lại màn hình Login và báo lỗi
                Platform.runLater(() -> {
                    NavigationManager.getInstance().loadView("/com/btl/team10/client/view/Login.fxml");
                });
            }
        });

        // Xử lý khi mất mạng hoặc Server sập
        loginTask.setOnFailed(e -> {
            NavigationManager.getInstance().switchState(UIState.ERROR);
        });

        // Bắt đầu chạy luồng mạng
        new Thread(loginTask).start();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}