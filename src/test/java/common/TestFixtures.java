package common;

import java.time.LocalDateTime;

public class TestFixtures {

    public record MockUser(int id, String username, String role, double balance) {}
    public record MockItem(int id, String name, String type, int sellerId) {}
    public record MockAuction(int id, int itemId, double startPrice, double currentPrice, 
                              int winnerId, LocalDateTime endTime, String status) {}

    public static MockUser createBidder(int id, String username, double balance) {
        return new MockUser(id, username, "BIDDER", balance);
    }

    public static MockUser createSeller() {
        return new MockUser(2, "seller_demo", "SELLER", 1000000.0);
    }

    public static MockItem createPokemonItem() {
        return new MockItem(10, "Pikachu Card", "CARD", 2);
    }

    public static MockAuction createActiveAuction(double price) {
        return new MockAuction(100, 10, price, price, 0, LocalDateTime.now().plusMinutes(15), "RUNNING");
    }
}