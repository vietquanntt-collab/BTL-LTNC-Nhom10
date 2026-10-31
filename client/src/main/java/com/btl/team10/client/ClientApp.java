package com.btl.team10.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Week 1 JavaFX shell; network integration arrives in week 2. */
public final class ClientApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(ClientApp.class.getResource("/fxml/shell.fxml"));
        stage.setTitle("Online Auction System - Nhom 10");
        stage.setScene(new Scene(loader.load(), 640, 360));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
