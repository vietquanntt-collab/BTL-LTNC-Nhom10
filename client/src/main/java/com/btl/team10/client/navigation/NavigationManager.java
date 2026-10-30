package com.btl.team10.client.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class NavigationManager {
    private static Stage primaryStage;

    public static void setStage(Stage stage) {
        primaryStage = stage;
    }

    public static void navigateTo(String fxmlFileName) {
        try {
            // Đường dẫn trỏ tới thư mục fxml trong resources
            FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource("/fxml/" + fxmlFileName));
            Parent root = loader.load();
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("Không thể mở màn hình: " + fxmlFileName);
            e.printStackTrace();
        }
    }
}