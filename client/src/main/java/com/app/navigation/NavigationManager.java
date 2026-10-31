package com.app.navigation;

import com.app.state.UIState;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NavigationManager {

    private static NavigationManager instance;
    private StackPane contentArea;
    private final Map<UIState, Node> stateCache = new HashMap<>();

    private NavigationManager() {}

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }

    // Chuyển UI State hiển thị trên Client Shell
    public void switchState(UIState state) {
        if (contentArea == null) return;

        try {
            if (!stateCache.containsKey(state)) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(state.getFxmlPath()));
                Node node = loader.load();
                stateCache.put(state, node);
            }
            contentArea.getChildren().setAll(stateCache.get(state));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Load một View bất kỳ vào Shell
    public void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node node = loader.load();
            contentArea.getChildren().setAll(node);
        } catch (IOException e) {
            switchState(UIState.ERROR);
            e.printStackTrace();
        }
    }
}