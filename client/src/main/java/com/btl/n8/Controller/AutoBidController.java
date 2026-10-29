package com.btl.n8.Controller;

import com.btl.n8.Model.Entity.Auction;
import com.btl.n8.Util.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Controller popup AutoBid.fxml.
 * Chỉ có 2 tham số: Max Price và Bid Step.
 * Bid Sniping đã bị loại bỏ hoàn toàn.
 * Anti-sniping được xử lý tự động phía server (gia hạn endTime).
 */
public class AutoBidController {

    // ── FXML ──────────────────────────────────────────────────────────────────
    @FXML private Label    lblItemName;
    @FXML private Label    lblCurrentPrice;
    @FXML private Label    statusBadge;
    @FXML private Label    msgLabel;

    @FXML private TextField maxPriceField;
    @FXML private TextField stepField;

    @FXML private Button activateBtn;
    @FXML private Button cancelAutoBidBtn;
    @FXML private Button closeBtn;

    // ── State ─────────────────────────────────────────────────────────────────
    private int     auctionId;
    private Auction auction;

    private Consumer<Boolean> onActiveChanged;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    // ── Public API ────────────────────────────────────────────────────────────

    public void setOnActiveChanged(Consumer<Boolean> cb) {
        this.onActiveChanged = cb;
    }

    public void initData(int auctionId, String itemName, Auction auction) {
        this.auctionId = auctionId;
        this.auction   = auction;

        lblItemName.setText(itemName != null ? itemName : "Auction #" + auctionId);
        if (auction != null) lblCurrentPrice.setText(fmt(auction.getCurrentPrice()));

        AutoBidManager.getInstance().setUICallback(auctionId, this::handleManagerEvent);

        if (AutoBidManager.getInstance().isActive(auctionId)) {
            restoreActiveUI();
        }
    }

    // ── FXML handlers ─────────────────────────────────────────────────────────

    @FXML
    public void handleActivate() {
        msgLabel.setText("");

        // Validate max price
        String maxStr = maxPriceField.getText().trim();
        if (maxStr.isEmpty()) { showMsg("Please enter Max Price", false); return; }
        BigDecimal maxPrice;
        try {
            maxPrice = new BigDecimal(maxStr);
            if (maxPrice.compareTo(BigDecimal.ZERO) <= 0) {
                showMsg("Max Price must be positive", false); return;
            }
        } catch (NumberFormatException e) {
            showMsg("Invalid Max Price format", false); return;
        }

        // Validate step
        String stepStr = stepField.getText().trim();
        if (stepStr.isEmpty()) { showMsg("Please enter Bid Step", false); return; }
        BigDecimal step;
        try {
            step = new BigDecimal(stepStr);
            if (step.compareTo(BigDecimal.ZERO) <= 0) {
                showMsg("Bid Step must be positive", false); return;
            }
        } catch (NumberFormatException e) {
            showMsg("Invalid Bid Step format", false); return;
        }

        if (auction != null && maxPrice.compareTo(auction.getCurrentPrice()) <= 0) {
            showMsg("Max Price must be above current price:\n" + fmt(auction.getCurrentPrice()), false);
            return;
        }

        // Kiểm tra balance: maxPrice không được vượt quá balance hiện tại
        com.btl.n8.Model.Entity.User currentUser = SessionManager.getInstance().getCurrentUser();
        BigDecimal balance = currentUser != null && currentUser.getBalance() != null
                ? currentUser.getBalance() : BigDecimal.ZERO;
        if (maxPrice.compareTo(balance) > 0) {
            showMsg(String.format("Số dư không đủ!\nBalance: %s\nMax Price: %s",
                    fmt(balance), fmt(maxPrice)), false);
            return;
        }

        int bidderId = SessionManager.getInstance().getCurrentUser().getId();

        AutoBidManager.getInstance().activate(
                auctionId, bidderId, maxPrice, step, auction);

        setActiveUI(true);
        showMsg("AutoBid active! Watching for outbids...", true);
        if (onActiveChanged != null) onActiveChanged.accept(true);
    }

    @FXML
    public void handleCancelAutoBid() {
        AutoBidManager.getInstance().cancel(auctionId);
    }

    @FXML
    public void handleClose() {
        Stage stage = (Stage) closeBtn.getScene().getWindow();
        stage.close();
    }

    // ── Manager event handler ─────────────────────────────────────────────────

    private void handleManagerEvent(String event) {
        if (event == null) return;
        String[] parts = event.split(":", 2);
        String   type  = parts[0];
        String   data  = parts.length > 1 ? parts[1] : "";

        switch (type) {
            case "CANCELLED" -> {
                setActiveUI(false);
                showMsg("AutoBid cancelled.", false);
                if (onActiveChanged != null) onActiveChanged.accept(false);
            }
            case "BID_PLACED" -> showMsg("🤖 AutoBid placed: " + fmtRaw(data), true);
            case "STOPPED" -> {
                setActiveUI(false);
                showMsg(data.isEmpty() ? "AutoBid stopped." : data, false);
                if (onActiveChanged != null) onActiveChanged.accept(false);
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void restoreActiveUI() {
        AutoBidManager.AutoBidSession s = AutoBidManager.getInstance().getSession(auctionId);
        if (s == null) return;

        maxPriceField.setText(s.maxPrice.toPlainString());
        stepField.setText(s.step.toPlainString());
        if (s.auction != null) lblCurrentPrice.setText(fmt(s.auction.getCurrentPrice()));

        setActiveUI(true);
        showMsg("AutoBid đang chạy ngầm...", true);
        if (onActiveChanged != null) onActiveChanged.accept(true);
    }

    private void setActiveUI(boolean active) {
        if (active) {
            statusBadge.setText("ACTIVE");
            statusBadge.setStyle("-fx-background-color: #00ff88; -fx-text-fill: #0a1628; " +
                    "-fx-padding: 3 10 3 10; -fx-background-radius: 99;");
            activateBtn.setVisible(false);
            activateBtn.setManaged(false);
            cancelAutoBidBtn.setVisible(true);
            cancelAutoBidBtn.setManaged(true);
            maxPriceField.setDisable(true);
            stepField.setDisable(true);
        } else {
            statusBadge.setText("OFF");
            statusBadge.setStyle("-fx-background-color: #333a4a; -fx-text-fill: #888; " +
                    "-fx-padding: 3 10 3 10; -fx-background-radius: 99;");
            activateBtn.setVisible(true);
            activateBtn.setManaged(true);
            cancelAutoBidBtn.setVisible(false);
            cancelAutoBidBtn.setManaged(false);
            maxPriceField.setDisable(false);
            stepField.setDisable(false);
        }
    }
    private void showMsg(String msg, boolean success) {
        Platform.runLater(() -> {
            msgLabel.setStyle(success ? "-fx-text-fill: #00ff88;" : "-fx-text-fill: #ff6b6b;");
            msgLabel.setText(msg);
        });
    }

    private String fmt(BigDecimal n) { return String.format("%,.0f ₫", n); }

    private String fmtRaw(String raw) {
        try { return fmt(new BigDecimal(raw)); } catch (Exception e) { return raw; }
    }

    public void detachUI() {
        AutoBidManager.getInstance().setUICallback(auctionId, null);
    }
}