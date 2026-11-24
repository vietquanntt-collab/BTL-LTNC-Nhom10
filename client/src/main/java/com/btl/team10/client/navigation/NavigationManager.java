package com.btl.team10.client.navigation;

import com.btl.team10.client.controller.AuctionDetailController;
import com.btl.team10.client.controller.AuctionListController;
import com.btl.team10.client.controller.LoginController;
import com.btl.team10.client.controller.SellerDashboardController;
import com.btl.team10.client.controller.AdminDashboardController;
import com.btl.team10.client.state.UIState;
import com.btl.team10.client.util.UiMotion;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NavigationManager {

    private static NavigationManager instance;
    private StackPane contentArea;
    private Object currentController;
    private final Map<UIState, Node> stateCache = new HashMap<>();

    private NavigationManager() {
    }

    public static NavigationManager getInstance() {
        if (instance == null) {
            instance = new NavigationManager();
        }
        return instance;
    }

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }

    public void switchState(UIState state) {
        if (contentArea == null) {
            return;
        }
        try {
            cleanupActiveController();
            if (!stateCache.containsKey(state)) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(state.getFxmlPath()));
                Node node = loader.load();
                stateCache.put(state, node);
            }
            Node target = stateCache.get(state);
            contentArea.getChildren().setAll(target);
            UiMotion.animateViewEntrance(target);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadView(String fxmlPath) {
        loadView(fxmlPath, null);
    }

    public <T> T loadView(String fxmlPath, Class<T> controllerType) {
        try {
            cleanupActiveController();
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node node = loader.load();
            Object controller = loader.getController();
            currentController = controller;
            contentArea.getChildren().setAll(node);
            UiMotion.animateViewEntrance(node);
            if (controllerType == null) {
                return null;
            }
            return controllerType.cast(controller);
        } catch (IOException e) {
            switchState(UIState.ERROR);
            e.printStackTrace();
            return null;
        }
    }

    private void cleanupActiveController() {
        if (currentController instanceof AuctionDetailController detailController) {
            detailController.cleanup();
        }
        if (currentController instanceof AuctionListController listController) listController.cleanup();
        if (currentController instanceof LoginController loginController) loginController.cleanup();
        if (currentController instanceof SellerDashboardController sellerController) sellerController.cleanup();
        if (currentController instanceof AdminDashboardController adminController) adminController.cleanup();
        currentController = null;
    }
}
