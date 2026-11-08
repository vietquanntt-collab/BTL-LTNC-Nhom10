package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.state.UIState;

public abstract class BaseStateController {

    protected NavigationManager getNavigationManager() {
        return NavigationManager.getInstance();
    }

    // Hàm hook để ghi đè logic load lại dữ liệu nếu cần
    public void onRetry() {
        getNavigationManager().switchState(UIState.LOADING);
    }
}