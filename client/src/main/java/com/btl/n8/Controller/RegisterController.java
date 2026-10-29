package com.btl.n8.Controller;

import com.btl.n8.DTO.RegisterRequest;
import com.btl.n8.DTO.RegisterResponse;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.LocalDateTimeAdapter;
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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.time.LocalDateTime;

public class RegisterController implements ServerResponseListener {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField rePasswordField;
    @FXML private Label         messageLabel;

    private ActionEvent pendingEvent;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @FXML
    public void initialize() {
        // FIX: remove trước rồi add — tránh listener bị stack khi quay lại màn hình
        // (cùng pattern đã được áp dụng đúng ở LoginController)
        ClientSocket.getInstance().removeListener(this);
        ClientSocket.getInstance().addListener(this);
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        messageLabel.setText("");
        messageLabel.setVisible(false);

        String username   = usernameField.getText().trim();
        String password   = passwordField.getText().trim();
        String rePassword = rePasswordField.getText().trim();

        if (username.isEmpty() || password.isEmpty() || rePassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin"); return;
        }
        if (username.length() < 3) {
            showError("Tên tài khoản phải ít nhất 3 ký tự"); return;
        }
        if (!password.equals(rePassword)) {
            showError("Mật khẩu không khớp"); return;
        }
        if (password.length() < 6) {
            showError("Mật khẩu phải ít nhất 6 ký tự"); return;
        }

        pendingEvent = event;
        ClientSocket.getInstance().sendMessage(new RegisterRequest(username, password));
    }

    @Override
    public void onRespone(JsonObject json) {
        if (!json.has("action")) return;
        String action = json.get("action").getAsString();
        if (!"REGISTER_SUCCESS".equals(action)) return;

        RegisterResponse res = gson.fromJson(json, RegisterResponse.class);
        Platform.runLater(() -> {
            ClientSocket.getInstance().removeListener(this);
            if (res.isSuccess()) {
                try { goLogin(pendingEvent); } catch (Exception e) { e.printStackTrace(); }
            } else {
                showError(res.getMessage() != null ? res.getMessage() : "Đăng ký thất bại");
            }
        });
    }

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);
    }

    public void goLogin(ActionEvent event) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }
}
