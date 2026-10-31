package com.app.controller;

import com.app.navigation.NavigationManager;
import com.app.state.UIState;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

public class MainShellController {

    @FXML
    private StackPane mainContentArea;

    @FXML
    public void initialize() {
        NavigationManager.getInstance().setContentArea(mainContentArea);
        // Mặc định hiển thị trạng thái Loading khi khởi tạo
        NavigationManager.getInstance().switchState(UIState.LOADING);
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