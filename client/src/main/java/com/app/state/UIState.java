package com.app.state;

public enum UIState {
    LOADING("/com/app/state/LoadingState.fxml"),
    EMPTY("/com/app/state/EmptyState.fxml"),
    ERROR("/com/app/state/ErrorState.fxml"),
    RECONNECTING("/com/app/state/ReconnectingState.fxml"),
    CLOSED("/com/app/state/ClosedState.fxml");

    private final String fxmlPath;

    UIState(String fxmlPath) {
        this.fxmlPath = fxmlPath;
    }

    public String getFxmlPath() {
        return fxmlPath;
    }
}