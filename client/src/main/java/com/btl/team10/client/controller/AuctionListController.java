package com.btl.team10.client.controller;

import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.common.dto.AuctionDto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.List;

/** Loads active auctions through the network adapter after login. */
public final class AuctionListController {
    @FXML private ListView<String> auctionListView;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        statusLabel.setText("Đang tải danh sách đấu giá...");
        Thread thread = new Thread(this::loadAuctions, "auction-list-loader");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void reload() {
        initialize();
    }

    private void loadAuctions() {
        try {
            List<AuctionDto> auctions = NetworkClient.getInstance().listAuctions();
            List<String> rows = auctions.stream()
                    .map(auction -> "#" + auction.auctionId() + "  |  item " + auction.itemId()
                            + "  |  giá hiện tại " + auction.currentPriceCents()
                            + " cents  |  " + auction.status())
                    .toList();
            Platform.runLater(() -> {
                auctionListView.setItems(FXCollections.observableArrayList(rows));
                statusLabel.setText(rows.isEmpty() ? "Chưa có phiên đấu giá đang mở." : "Đã tải xong.");
            });
        } catch (Exception error) {
            Platform.runLater(() -> statusLabel.setText("Không tải được dữ liệu: " + error.getMessage()));
        }
    }
}
