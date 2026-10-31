package com.app.controller;

import com.app.navigation.NavigationManager;
import com.app.state.UIState;

public abstract class BaseStateController {

    protected NavigationManager getNavigationManager() {
        return NavigationManager.getInstance();
    }

    // Hàm hook để ghi đè logic load lại dữ liệu nếu cần
    public void onRetry() {
        getNavigationManager().switchState(UIState.LOADING);
    }
}