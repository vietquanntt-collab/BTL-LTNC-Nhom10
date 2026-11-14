package com.btl.team10.client.controller;

import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.AuctionDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;

import java.util.Collections;
import java.util.List;

/** Loads active auctions through the network adapter after login. */
public final class AuctionListController {
    @FXML private ListView<String> auctionListView;
    @FXML private Label statusLabel;

    private List<AuctionDto> currentAuctions = Collections.emptyList();

    @FXML
    private void initialize() {
        auctionListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openSelectedAuction();
            }
        });
        loadAuctionsAsync();
    }

    @FXML
    private void reload() {
        loadAuctionsAsync();
    }

    @FXML
    private void openSelectedAuction() {
        int index = auctionListView.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= currentAuctions.size()) {
            statusLabel.setText("Vui lòng chọn một phiên đấu giá trong danh sách.");
            return;
        }
        AuctionDto selected = currentAuctions.get(index);
        AuctionDetailController detailController = NavigationManager.getInstance()
                .loadView("/com/btl/team10/client/view/AuctionDetail.fxml", AuctionDetailController.class);
        if (detailController != null) {
            detailController.initAuction(selected);
        }
    }

    private void loadAuctionsAsync() {
        statusLabel.setText("Đang tải danh sách đấu giá...");
        Thread thread = new Thread(this::loadAuctions, "auction-list-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadAuctions() {
        try {
            List<AuctionDto> auctions = NetworkClient.getInstance().listAuctions();
            List<String> rows = auctions.stream()
                    .map(auction -> "#" + auction.auctionId() + " | Vật phẩm #" + auction.itemId()
                            + " | Giá hiện tại: " + auction.currentPriceCents() + " cents"
                            + " | Bước giá: +" + auction.minIncrementCents()
                            + " | Trạng thái: " + auction.status())
                    .toList();
            Platform.runLater(() -> {
                this.currentAuctions = auctions;
                auctionListView.setItems(FXCollections.observableArrayList(rows));
                statusLabel.setText(rows.isEmpty() ? "Chưa có phiên đấu giá đang mở." : "Đã tải " + rows.size() + " phiên.");
            });
        } catch (Exception error) {
            Platform.runLater(() -> statusLabel.setText("Không tải được dữ liệu: " + error.getMessage()));
        }
    }
}