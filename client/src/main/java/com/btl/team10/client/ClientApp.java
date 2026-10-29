package com.btl.team10.client;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.state.UIState;
import javafx.application.Application;
import javafx.stage.Stage;

public class ClientApp extends Application {
    private static UIState currentUIState = UIState.LOADING;

    @Override
    public void start(Stage primaryStage) {
        // Gắn stage chính vào NavigationManager
        NavigationManager.setStage(primaryStage);
        primaryStage.setTitle("Online Auction System - Client");

        // Gọi màn hình đăng nhập đầu tiên
        NavigationManager.navigateTo("login.fxml");
    }

    public static void setUIState(UIState state) {
        currentUIState = state;
        System.out.println("Trạng thái UI đã đổi thành: " + currentUIState);
    }

    public static UIState getUIState() {
        return currentUIState;
    }

    public static void main(String[] args) {
        launch(args);
    }
}