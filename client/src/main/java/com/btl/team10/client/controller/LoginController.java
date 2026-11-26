package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.client.util.UiMotion;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** Màn hình xác thực với 2 nút chọn chế độ và hiệu ứng nhiều vật phẩm to nhỏ rơi ngoài sảnh. */
public final class LoginController {

    private static final String[] FALLING_ITEM_KEYS = {
            "item_1",
            "item_2",
            "item_3",
            "item_4",
            "item_5"
    };

    private static final int FALLING_COUNT = 18;

    private static final double[] X_RATIOS = {
            0.05, 0.14, 0.24, 0.33, 0.43, 0.57, 0.67, 0.76, 0.86,
            0.95, 0.09, 0.19, 0.29, 0.50, 0.71, 0.81, 0.91, 0.38
    };

    private static final double[] ITEM_SIZES = {
            88.0, 36.0, 64.0, 44.0, 92.0, 34.0, 72.0, 40.0, 86.0,
            38.0, 56.0, 94.0, 32.0, 48.0, 82.0, 36.0, 68.0, 52.0
    };

    private static final double[] FALL_SPEEDS = {
            76.0, 44.0, 62.0, 48.0, 80.0, 42.0, 66.0, 46.0, 74.0,
            45.0, 58.0, 82.0, 40.0, 52.0, 72.0, 44.0, 64.0, 55.0
    };

    private static final double BG_PATTERN_SCALE = 0.55;

    @FXML private Pane bgPane;
    @FXML private Pane ambientParticlesPane;
    @FXML private ImageView loginBgView;
    @FXML private VBox authCardBox;
    @FXML private VBox formFieldsContainer;
    @FXML private Button loginTabBtn;
    @FXML private Button registerTabBtn;
    @FXML private Button submitButton;
    @FXML private Button toggleModeButton;
    @FXML private Label formTitleLabel;
    @FXML private Label formSubtitleLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private VBox confirmGroup;

    private final List<FallingItem> fallingItems = new ArrayList<>();
    private Timeline fallingTimeline;
    private boolean formExpanded;
    private boolean registerMode;
    private boolean disposed;

    @FXML
    private void initialize() {
        setupTiledBackdrop();
        setupFallingItems();
        collapseForm();
    }

    private void setupTiledBackdrop() {
        Image backdrop = StageAssetLoader.loadRawBackdrop("login_bg");
        if (backdrop == null || backdrop.getWidth() <= 0 || backdrop.getHeight() <= 0) {
            return;
        }
        loginBgView.setVisible(false);
        loginBgView.setManaged(false);

        double tileW = backdrop.getWidth() * BG_PATTERN_SCALE;
        double tileH = backdrop.getHeight() * BG_PATTERN_SCALE;

        Rectangle tiledBgRect = new Rectangle();
        tiledBgRect.widthProperty().bind(bgPane.widthProperty());
        tiledBgRect.heightProperty().bind(bgPane.heightProperty());
        tiledBgRect.setFill(new ImagePattern(backdrop, 0, 0, tileW, tileH, false));
        tiledBgRect.setMouseTransparent(true);

        bgPane.getChildren().add(0, tiledBgRect);
    }

    private void setupFallingItems() {
        if (ambientParticlesPane == null) {
            return;
        }
        Rectangle particlesClip = new Rectangle();
        particlesClip.widthProperty().bind(ambientParticlesPane.widthProperty());
        particlesClip.heightProperty().bind(ambientParticlesPane.heightProperty());
        ambientParticlesPane.setClip(particlesClip);
        ambientParticlesPane.getChildren().clear();
        fallingItems.clear();

        List<Image> sprites = new ArrayList<>();
        for (String key : FALLING_ITEM_KEYS) {
            Image sprite = StageAssetLoader.loadCutoutSprite(key);
            if (sprite != null) {
                sprites.add(sprite);
            }
        }
        if (sprites.isEmpty()) {
            return;
        }

        for (int i = 0; i < FALLING_COUNT; i++) {
            Image sprite = sprites.get(i % sprites.size());
            double size = ITEM_SIZES[i % ITEM_SIZES.length];
            double xRatio = X_RATIOS[i % X_RATIOS.length];
            double speed = FALL_SPEEDS[i % FALL_SPEEDS.length];

            ImageView view = new ImageView(sprite);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setOpacity(size >= 70.0 ? 0.96 : (size >= 50.0 ? 0.88 : 0.78));
            view.setMouseTransparent(true);

            // Trải sẵn tọa độ Y khắp màn hình ngay từ đầu để không bị trống
            double initialY = -80.0 + ((i * 840.0) / FALLING_COUNT);
            double swayAmp = size >= 70.0 ? 22.0 : 14.0;
            view.setLayoutX(1280.0 * xRatio - (size / 2.0));
            view.setLayoutY(initialY);

            ambientParticlesPane.getChildren().add(view);
            fallingItems.add(new FallingItem(view, xRatio, speed, swayAmp, initialY, i * 0.95));
        }

        fallingTimeline = new Timeline(new KeyFrame(Duration.millis(25), e -> updateFallingFrame()));
        fallingTimeline.setCycleCount(Animation.INDEFINITE);
        fallingTimeline.play();
    }

    private void updateFallingFrame() {
        double paneW = ambientParticlesPane.getWidth() > 0 ? ambientParticlesPane.getWidth() : 1280.0;
        double paneH = ambientParticlesPane.getHeight() > 0 ? ambientParticlesPane.getHeight() : 800.0;
        double dt = 0.025;

        for (FallingItem item : fallingItems) {
            item.phase += dt * 1.65;
            item.y += item.speedPxPerSec * dt;
            double resetMargin = item.node.getFitHeight() + 24.0;
            if (item.y > paneH + resetMargin) {
                item.y = -resetMargin;
            }
            double baseX = paneW * item.xRatio - (item.node.getFitWidth() / 2.0);
            double swayX = Math.sin(item.phase) * item.swayAmplitude;
            item.node.setLayoutX(baseX + swayX);
            item.node.setLayoutY(item.y);
            item.node.setRotate(Math.sin(item.phase * 0.85) * 15.0);
        }
    }

    @FXML
    private void collapseForm() {
        formExpanded = false;
        registerMode = false;
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();
        loginTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), false);
        registerTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), false);
        formTitleLabel.setText("Chào mừng đến với Plushie Gala");
        formSubtitleLabel.setText("Chọn Đăng nhập hoặc Đăng ký bên dưới để bắt đầu.");
        formFieldsContainer.setVisible(false);
        formFieldsContainer.setManaged(false);
    }

    @FXML
    private void switchToLogin() {
        if (!formExpanded || registerMode) {
            applyModeUi(false);
        }
        usernameField.requestFocus();
    }

    @FXML
    private void switchToRegister() {
        if (!formExpanded || !registerMode) {
            applyModeUi(true);
        }
        usernameField.requestFocus();
    }

    @FXML
    private void handleToggleMode() {
        applyModeUi(!registerMode);
        usernameField.requestFocus();
    }

    private void applyModeUi(boolean register) {
        boolean firstOpen = !formExpanded;
        formExpanded = true;
        registerMode = register;
        hideFeedback();
        passwordField.clear();
        confirmPasswordField.clear();

        formFieldsContainer.setVisible(true);
        formFieldsContainer.setManaged(true);
        if (firstOpen) {
            UiMotion.animateEntrance(formFieldsContainer);
        }

        loginTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), !register);
        registerTabBtn.pseudoClassStateChanged(PseudoClass.getPseudoClass("active"), register);
        formTitleLabel.setText(register ? "Tạo tài khoản mới" : "Chào mừng trở lại");
        formSubtitleLabel.setText(register
                ? "Điền thông tin bên dưới để tạo tài khoản đấu giá."
                : "Nhập tài khoản và mật khẩu để vào sàn đấu giá.");
        confirmGroup.setVisible(register);
        confirmGroup.setManaged(register);
        submitButton.setText(register ? "Tạo tài khoản" : "Đăng nhập");
        toggleModeButton.setText(register ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký ngay");
    }

    @FXML
    private void handleSubmit() {
        if (!formExpanded) {
            switchToLogin();
            return;
        }
        String account = usernameField.getText().trim();
        String password = passwordField.getText();
        if (account.isBlank() || password.isBlank()) {
            showError("Vui lòng nhập tài khoản và mật khẩu.");
            return;
        }
        if (registerMode && (account.length() < 3 || password.length() < 6)) {
            showError("Tên tài khoản cần ít nhất 3 ký tự; mật khẩu cần ít nhất 6 ký tự.");
            return;
        }
        if (registerMode && !password.equals(confirmPasswordField.getText())) {
            showError("Mật khẩu xác nhận chưa khớp.");
            return;
        }
        boolean registering = registerMode;
        setControlsDisabled(true);
        submitButton.setText(registering ? "Đang tạo tài khoản…" : "Đang đăng nhập…");
        Thread worker = new Thread(() -> {
            try {
                if (registering) {
                    NetworkClient.getInstance().register(account, password);
                } else {
                    NetworkClient.getInstance().login(account, password);
                }
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    if (registering) {
                        setControlsDisabled(false);
                        applyModeUi(false);
                        usernameField.setText(account);
                        showFeedback("Đăng ký thành công. Nhập mật khẩu để đăng nhập.", false);
                    } else {
                        cleanup();
                        MainShellController.onSessionUpdated();
                        NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionList.fxml");
                    }
                });
            } catch (Exception error) {
                Platform.runLater(() -> {
                    if (disposed) {
                        return;
                    }
                    setControlsDisabled(false);
                    submitButton.setText(registering ? "Tạo tài khoản" : "Đăng nhập");
                    showError(error.getMessage() == null
                            ? "Không thể kết nối. Kiểm tra server và thử lại."
                            : error.getMessage());
                });
            }
        }, "auction-auth");
        worker.setDaemon(true);
        worker.start();
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
        showFeedback(message, true);
    }

    private void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.setStyle(error
                ? "-fx-background-color: #421f2b; -fx-text-fill: #fecaca; -fx-padding: 10 12; -fx-background-radius: 8;"
                : "-fx-background-color: #173e35; -fx-text-fill: #bbf7d0; -fx-padding: 10 12; -fx-background-radius: 8;");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    public void cleanup() {
        disposed = true;
        if (fallingTimeline != null) {
            fallingTimeline.stop();
            fallingTimeline = null;
        }
    }

    private static final class FallingItem {
        private final ImageView node;
        private final double xRatio;
        private final double speedPxPerSec;
        private final double swayAmplitude;
        private double y;
        private double phase;

        private FallingItem(
                ImageView node,
                double xRatio,
                double speedPxPerSec,
                double swayAmplitude,
                double y,
                double phase) {
            this.node = node;
            this.xRatio = xRatio;
            this.speedPxPerSec = speedPxPerSec;
            this.swayAmplitude = swayAmplitude;
            this.y = y;
            this.phase = phase;
        }
    }
}
