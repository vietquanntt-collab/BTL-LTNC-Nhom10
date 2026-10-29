package com.btl.n8.Controller;

import com.btl.n8.Model.Entity.Admin;
import com.btl.n8.Model.Entity.Bidder;
import com.btl.n8.Model.Entity.Seller;
import com.btl.n8.Model.Entity.User;
import com.btl.n8.Model.Enums.Role;
import com.btl.n8.DTO.LoginRequest;
import com.btl.n8.DTO.LoginResponse;
import com.btl.n8.Network.ClientSocket;
import com.btl.n8.Network.ServerResponseListener;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;

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

import java.math.BigDecimal;

public class LoginController implements ServerResponseListener {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label messageLabel;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    private ActionEvent lastEvent;

    @FXML
    public void initialize() {
        // FIX: remove trước rồi add — đảm bảo chỉ có đúng 1 instance
        // listener dù load lại FXML nhiều lần (logout → login lại)
        ClientSocket.getInstance().removeListener(this);
        ClientSocket.getInstance().addListener(this);
    }

    public void handleLogin(ActionEvent event) {
        this.lastEvent = event;
        messageLabel.setText("Connecting...");
        messageLabel.setVisible(true);

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter username and password");
            messageLabel.setVisible(true);
            return;
        }

        new Thread(() -> {
            // FIX: nếu socket cũ đã chết (server restart), reset trước khi connect lại
            if (!ClientSocket.getInstance().isConnected()) {
                ClientSocket.getInstance().reset();
                boolean ok = ClientSocket.getInstance().connect();
                if (!ok) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Cannot connect to server!");
                        messageLabel.setVisible(true);
                    });
                    return;
                }
            }
            ClientSocket.getInstance().sendMessage(new LoginRequest(username, password));
        }).start();
    }

    @Override
    public void onRespone(JsonObject response) {
        String action = response.get("action").getAsString();
        if (!"LOGIN_SUCCESS".equals(action)) return;

        LoginResponse res = gson.fromJson(response, LoginResponse.class);

        Platform.runLater(() -> {
            if (!res.isSuccess()) {
                messageLabel.setText(res.getMessage());
                messageLabel.setVisible(true);
                return;
            }

            // Remove ngay khi xử lý xong — không để listener lơ lửng
            ClientSocket.getInstance().removeListener(this);

            User user = buildUser(res);
            SessionManager.getInstance().setCurrentUser(user);
            SessionManager.getInstance().setSessionId(res.getSessionId());

            try {
                String fxml = res.getRole() == Role.ADMIN
                        ? "/fxml/admin.fxml"
                        : "/fxml/home.fxml";
                Parent root = FXMLLoader.load(getClass().getResource(fxml));
                Stage stage = (Stage)((Node)lastEvent.getSource()).getScene().getWindow();
                stage.setScene(new Scene(root));
                stage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private User buildUser(LoginResponse res) {
        switch (res.getRole()) {
            case BIDDER:
                return new Bidder(res.getUserId(), res.getUsername(), "",
                        res.getBalance() != null ? res.getBalance() : BigDecimal.ZERO);
            case SELLER:
                Seller seller = new Seller(res.getUserId(), res.getUsername(), "");
                seller.setBalance(res.getBalance() != null ? res.getBalance() : BigDecimal.ZERO);
                return seller;
            default:
                return new Admin(res.getUserId(), res.getUsername(), "");
        }
    }

    public void goRegister(ActionEvent event) throws Exception {
        ClientSocket.getInstance().removeListener(this);
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/register.fxml"));
        Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        stage.setScene(new Scene(root));
        stage.show();
    }
}