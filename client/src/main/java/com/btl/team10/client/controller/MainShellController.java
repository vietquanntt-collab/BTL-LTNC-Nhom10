package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.state.UIState;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

public class MainShellController {

    @FXML
    private StackPane mainContentArea;

    @FXML
    public void initialize() {
        NavigationManager.getInstance().setContentArea(mainContentArea);
        // Hiển thị form Login khi vừa mở ứng dụng
        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/login.fxml");
    }

    @FXML
    private void onShowLoading() {
        NavigationManager.getInstance().switchState(UIState.LOADING);
    }

    @FXML
    private void onShowEmpty() {
        NavigationManager.getInstance().switchState(UIState.EMPTY);
    }

    @FXML
    private void onShowError() {
        NavigationManager.getInstance().switchState(UIState.ERROR);
    }

    @FXML
    private void onShowReconnecting() {
        NavigationManager.getInstance().switchState(UIState.RECONNECTING);
    }

    @FXML
    private void onShowClosed() {
        NavigationManager.getInstance().switchState(UIState.CLOSED);
    }
}
