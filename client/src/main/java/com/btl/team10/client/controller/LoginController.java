package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.client.util.UiMotion;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.concurrent.ThreadLocalRandom;

public final class LoginController {

    private static final String TAB_ACTIVE_STYLE =
            "-fx-background-color: linear-gradient(to right, #f59e0b, #d97706); -fx-text-fill: #0f172a; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";
    private static final String TAB_INACTIVE_STYLE =
            "-fx-background-color: transparent; -fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;";

    private static final String INPUT_NORMAL_STYLE =
            "-fx-background-color: rgba(255, 255, 255, 0.08); -fx-text-fill: white; -fx-prompt-text-fill: #64748b; -fx-border-color: rgba(148, 163, 184, 0.35); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 0 12; -fx-font-size: 13px;";
    private static final String INPUT_FOCUS_STYLE =
            "-fx-background-color: rgba(255, 255, 255, 0.13); -fx-text-fill: white; -fx-prompt-text-fill: #94a3b8; -fx-border-color: #facc15; -fx-border-width: 1.4; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 0 12; -fx-font-size: 13px;";

    @FXML private Pane bgPane;
    @FXML private ImageView loginBgView;
    @FXML private Pane ambientParticlesPane;
    @FXML private VBox authCardBox;
    @FXML private Circle liveStatusDot;

    @FXML private Button loginTabBtn;
    @FXML private Button registerTabBtn;
    @FXML private Label formTitleLabel;
    @FXML private Label formSubtitleLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private VBox confirmGroup;
    @FXML private PasswordField confirmPasswordField;
    @FXML private VBox demoQuickBox;
    @FXML private Button submitButton;
    @FXML private Button toggleModeButton;

    private boolean registerMode = false;

    @FXML
    private void initialize() {
        setupBackgroundImage();
        startAmbientStageMotion();
        bindInputFocusGlow(usernameField);
        bindInputFocusGlow(passwordField);
        bindInputFocusGlow(confirmPasswordField);
        UiMotion.startLiveBlink(liveStatusDot);
        applyModeUi(false);
    }

    private void bindInputFocusGlow(Control field) {
        if (field == null) {
            return;
        }
        field.focusedProperty().addListener((obs, wasFocused, isFocused) ->
                field.setStyle(isFocused ? INPUT_FOCUS_STYLE : INPUT_NORMAL_STYLE));
    }

    private void startAmbientStageMotion() {
        if (loginBgView != null) {
            ScaleTransition kenBurns = new ScaleTransition(Duration.seconds(14), loginBgView);
            kenBurns.setFromX(1.0);
            kenBurns.setFromY(1.0);
            kenBurns.setToX(1.045);
            kenBurns.setToY(1.045);
            kenBurns.setAutoReverse(true);
            kenBurns.setCycleCount(Animation.INDEFINITE);
            kenBurns.play();
        }

        if (ambientParticlesPane != null) {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < 22; i++) {
                Circle sparkle = new Circle(rnd.nextDouble(1.5, 3.6), Color.web("#fde047", rnd.nextDouble(0.35, 0.85)));
                sparkle.setLayoutX(rnd.nextDouble(40, 1080));
                sparkle.setLayoutY(rnd.nextDouble(120, 680));
                ambientParticlesPane.getChildren().add(sparkle);

                Duration cycle = Duration.seconds(rnd.nextDouble(5.5, 10.5));
                TranslateTransition floatUp = new TranslateTransition(cycle, sparkle);
                floatUp.setByY(rnd.nextDouble(-140, -60));
                floatUp.setByX(rnd.nextDouble(-30, 30));
                floatUp.setAutoReverse(true);
                floatUp.setCycleCount(Animation.INDEFINITE);

                FadeTransition shimmer = new FadeTransition(Duration.seconds(rnd.nextDouble(1.8, 3.5)), sparkle);
                shimmer.setFromValue(0.15);
                shimmer.setToValue(0.90);
                shimmer.setAutoReverse(true);
                shimmer.setCycleCount(Animation.INDEFINITE);

                ParallelTransition pt = new ParallelTransition(floatUp, shimmer);
                pt.setDelay(Duration.millis(rnd.nextDouble(0, 2000)));
                pt.play();
            }
        }
    }

    private void setupBackgroundImage() {
        Image bgImage = StageAssetLoader.loadRawBackdrop("login_bg");
        if (bgImage != null && loginBgView != null && bgPane != null) {
            loginBgView.setImage(bgImage);
            loginBgView.setPreserveRatio(false);
            loginBgView.fitWidthProperty().bind(bgPane.widthProperty());
            loginBgView.fitHeightProperty().bind(bgPane.heightProperty());

            Runnable updateViewport = () -> updateBackgroundViewport(bgImage);
            bgPane.widthProperty().addListener((obs, oldVal, newVal) -> updateViewport.run());
            bgPane.heightProperty().addListener((obs, oldVal, newVal) -> updateViewport.run());
            updateViewport.run();
        }
    }

    private void updateBackgroundViewport(Image bgImage) {
        double paneW = bgPane.getWidth();
        double paneH = bgPane.getHeight();
        double imgW = bgImage.getWidth();
        double imgH = bgImage.getHeight();
        if (paneW <= 0 || paneH <= 0 || imgW <= 0 || imgH <= 0) {
            return;
        }

        double paneRatio = paneW / paneH;
        double imgRatio = imgW / imgH;

        double minX = 0;
        double minY = 0;
        double cropW = imgW;
        double cropH = imgH;

        if (paneRatio < imgRatio) {
            cropW = imgH * paneRatio;
            minX = (imgW - cropW) / 2.0;
        } else if (paneRatio > imgRatio) {
            cropH = imgW / paneRatio;
            minY = (imgH - cropH) / 2.0;
        }

        loginBgView.setViewport(new Rectangle2D(minX, minY, cropW, cropH));
    }

    @FXML
    private void switchToLogin() {
        if (registerMode) {
            applyModeUi(false);
        }
    }

    @FXML
    private void switchToRegister() {
        if (!registerMode) {
            applyModeUi(true);
        }
    }

    @FXML
    private void handleToggleMode() {
        applyModeUi(!registerMode);
    }

    private void applyModeUi(boolean toRegister) {
        registerMode = toRegister;
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();

        if (registerMode) {
            loginTabBtn.setStyle(TAB_INACTIVE_STYLE);
            registerTabBtn.setStyle(TAB_ACTIVE_STYLE);
            formTitleLabel.setText("TẠO TÀI KHOẢN MỚI");
            formSubtitleLabel.setText("Đăng ký tài khoản để tham gia đấu giá trực tiếp");
            confirmGroup.setVisible(true);
            confirmGroup.setManaged(true);
            demoQuickBox.setVisible(false);
            demoQuickBox.setManaged(false);
            submitButton.setText("Hoàn tất đăng ký →");
            submitButton.setStyle("-fx-background-color: linear-gradient(to right, #22c55e, #16a34a); -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
            toggleModeButton.setText("Đã có tài khoản? Quay lại đăng nhập");
        } else {
            loginTabBtn.setStyle(TAB_ACTIVE_STYLE);
            registerTabBtn.setStyle(TAB_INACTIVE_STYLE);
            formTitleLabel.setText("CHÀO MỪNG TRỞ LẠI");
            formSubtitleLabel.setText("Đăng nhập để bước vào khán phòng đấu giá trực tiếp");
            confirmGroup.setVisible(false);
            confirmGroup.setManaged(false);
            demoQuickBox.setVisible(true);
            demoQuickBox.setManaged(true);
            submitButton.setText("Vào Khán Phòng Đấu Giá →");
            submitButton.setStyle("-fx-background-color: linear-gradient(to right, #f59e0b, #d97706); -fx-text-fill: #0f172a; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
            toggleModeButton.setText("Chưa có tài khoản? Đăng ký ngay");
        }
        UiMotion.pulse(authCardBox, 1.015);
    }

    @FXML
    private void fillDemoBidder() {
        switchToLogin();
        usernameField.setText("bidder1");
        passwordField.setText("bidder123");
        hideFeedback();
        UiMotion.pulse(submitButton, 1.04);
    }

    @FXML
    private void fillDemoSeller() {
        switchToLogin();
        usernameField.setText("seller");
        passwordField.setText("seller123");
        hideFeedback();
        UiMotion.pulse(submitButton, 1.04);
    }

    @FXML
    private void fillDemoAdmin() {
        switchToLogin();
        usernameField.setText("admin");
        passwordField.setText("admin123");
        hideFeedback();
        UiMotion.pulse(submitButton, 1.04);
    }

    @FXML
    private void handleSubmit() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            showError("Tài khoản và mật khẩu không được để trống.");
            return;
        }

        if (registerMode) {
            if (username.length() < 3) {
                showError("Tên đăng nhập phải có ít nhất 3 ký tự.");
                return;
            }
            if (password.length() < 6) {
                showError("Mật khẩu đăng ký phải có ít nhất 6 ký tự.");
                return;
            }
            if (!password.equals(confirmPasswordField.getText())) {
                showError("Mật khẩu xác nhận không khớp.");
                return;
            }
            executeRegister(username, password);
        } else {
            executeLogin(username, password);
        }
    }

    private void executeLogin(String username, String password) {
        setControlsDisabled(true);
        submitButton.setText("Đang kết nối tới sàn...");
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().login(username, password);
                Platform.runLater(() -> {
                    MainShellController.onSessionUpdated();
                    NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    submitButton.setText("Vào Khán Phòng Đấu Giá →");
                    showError(e.getMessage() != null ? e.getMessage() : "Không thể đăng nhập.");
                });
            }
        }, "auction-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void executeRegister(String username, String password) {
        setControlsDisabled(true);
        submitButton.setText("Đang tạo tài khoản...");
        Thread thread = new Thread(() -> {
            try {
                NetworkClient.getInstance().register(username, password);
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    applyModeUi(false);
                    usernameField.setText(username);
                    passwordField.requestFocus();
                    showSuccess("Đăng ký thành công! Vui lòng nhập mật khẩu để đăng nhập.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setControlsDisabled(false);
                    submitButton.setText("Hoàn tất đăng ký →");
                    showError(e.getMessage() != null ? e.getMessage() : "Đăng ký thất bại.");
                });
            }
        }, "auction-register");
        thread.setDaemon(true);
        thread.start();
    }

    private void setControlsDisabled(boolean disabled) {
        loginTabBtn.setDisable(disabled);
        registerTabBtn.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        confirmPasswordField.setDisable(disabled);
        submitButton.setDisable(disabled);
        toggleModeButton.setDisable(disabled);
    }

    public void showError(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-background-color: rgba(220, 38, 38, 0.22); -fx-text-fill: #fca5a5; -fx-border-color: rgba(248, 113, 113, 0.5); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
        UiMotion.shake(authCardBox);
    }

    private void showSuccess(String message) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle("-fx-background-color: rgba(22, 163, 74, 0.22); -fx-text-fill: #86efac; -fx-border-color: rgba(74, 222, 128, 0.5); -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-font-size: 12px; -fx-font-weight: bold;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }
}