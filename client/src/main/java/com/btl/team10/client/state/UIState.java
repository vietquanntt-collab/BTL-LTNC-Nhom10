package com.btl.team10.client.state;

public enum UIState {
    LOADING("/com/btl/team10/client/state/LoadingState.fxml"),
    EMPTY("/com/btl/team10/client/state/EmptyState.fxml"),
    ERROR("/com/btl/team10/client/state/ErrorState.fxml"),
    RECONNECTING("/com/btl/team10/client/state/ReconnectingState.fxml"),
    CLOSED("/com/btl/team10/client/state/ClosedState.fxml");

    private final String fxmlPath;

    UIState(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}