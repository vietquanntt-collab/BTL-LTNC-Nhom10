package com.btl.team10.server.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import com.btl.team10.common.model.ItemType;
import com.btl.team10.common.model.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DomainTest {
  private static final Instant START = Instant.parse("2026-11-24T00:00:00Z");
  private static final Instant END = START.plusSeconds(60);

  @Test
  void rolesApplyPolymorphicPoliciesWithoutGrantingUnrelatedPermissions() {
    User bidder = User.from(new UserDto(2, "bidder", Role.BIDDER, 1000, 0, START, START));
    User seller = User.of(3, "seller", Role.SELLER);
    User admin = User.of(4, "admin", Role.ADMIN);
    assertInstanceOf(Bidder.class, bidder);
    assertInstanceOf(Seller.class, seller);
    assertInstanceOf(Admin.class, admin);
    assertTrue(bidder.canBid());
    assertFalse(bidder.canCreateItem());
    assertFalse(bidder.canManage(2));
    assertTrue(seller.canCreateItem());
    assertTrue(seller.canManage(3));
    assertFalse(seller.canManage(99));
    assertFalse(seller.canBid());
    assertTrue(admin.canManage(99));
    assertFalse(admin.canBid());
    assertFalse(admin.canCreateItem());
    assertEquals(Role.SELLER, seller.getRole());
    assertEquals(Role.ADMIN, admin.getRole());
    assertEquals("bidder", bidder.getAccount());
    List<Entity> entities = List.of(bidder, seller, admin);
    assertEquals(List.of("BIDDER @bidder", "SELLER @seller", "ADMIN @admin"),
        entities.stream().map(Entity::printInfo).toList());
  }

  @Test
  void usersRejectMissingIdentityAndRole() {
    assertThrows(IllegalArgumentException.class, () -> new Bidder(-1, "bidder"));
    assertThrows(IllegalArgumentException.class, () -> new Seller(0, "seller"));
    assertThrows(IllegalArgumentException.class, () -> new Admin(1, null));
    assertThrows(IllegalArgumentException.class, () -> new Admin(1, " "));
    assertThrows(NullPointerException.class, () -> User.of(1, "user", null));
    assertThrows(NullPointerException.class, () -> User.from(null));
  }

  @Test
  void factoryCreatesAndRestoresAllCatalogSubtypesWithoutChangingWireDtos() {
    for (ItemType type : ItemType.values()) {
      ItemFactory factory = ItemFactory.forType(type);
      Item draft = factory.createDraft(new CreateItemCommand(2, "  Rare item  ", " detail ", type, "YWJj"));
      assertEquals(0, draft.getId());
      assertEquals(2, draft.getSellerId());
      assertEquals("Rare item", draft.getName());
      assertEquals("detail", draft.getDescription());
      assertEquals("YWJj", draft.getImageBase64());
      assertEquals(type, draft.getType());
      assertFalse(draft.getCategoryLabel().isBlank());
      assertTrue(draft.printInfo().endsWith(": Rare item"));
      ItemDto dto = new ItemDto(10, 2, draft.getName(), draft.getDescription(), type,
          draft.getImageBase64(), START, END);
      Item restored = ItemFactory.from(dto);
      assertEquals(dto, restored.toDto());
      assertEquals(draft.getClass(), restored.getClass());
      assertTrue(switch (type) {
        case CARD -> restored instanceof Card;
        case FIGURE -> restored instanceof Figure;
        case POSTER -> restored instanceof Poster;
      });
    }
  }

  @Test
  void factoryRejectsMismatchedCreatorsAndItemsEnforceTheirInvariants() {
    ItemFactory cards = ItemFactory.forType(ItemType.CARD);
    assertThrows(IllegalArgumentException.class, () -> cards.restore(
        new ItemDto(1, 2, "Figure", "", ItemType.FIGURE, null, START, END)));
    assertThrows(IllegalArgumentException.class, () -> cards.createDraft(
        new CreateItemCommand(2, "Figure", "", ItemType.FIGURE, null)));
    assertThrows(NullPointerException.class, () -> cards.createDraft(null));
    assertThrows(NullPointerException.class, () -> cards.restore(null));
    assertThrows(NullPointerException.class, () -> ItemFactory.forType(null));
    assertThrows(NullPointerException.class, () -> ItemFactory.from(null));
    assertThrows(IllegalArgumentException.class, () -> new Card(1, 0, "Item", "", null, START, END));
    assertThrows(IllegalArgumentException.class, () -> new Card(1, 2, " ", "", null, START, END));
    assertThrows(IllegalArgumentException.class, () -> new Card(1, 2, "x".repeat(201), "", null, START, END));
    assertThrows(NullPointerException.class, () -> new Card(1, 2, "Item", "", null, null, END));
    Item normalized = new Card(1, 2, "Item", null, " ", START, END);
    assertEquals("", normalized.getDescription());
    assertNull(normalized.getImageBase64());
  }

  @Test
  void auctionAcceptsOnlyLiveStatesWithinTheHalfOpenTimeWindow() {
    for (AuctionStatus status : AuctionStatus.values()) {
      Auction auction = new Auction(1, status, START, END, 100_000, 10_000);
      boolean live = status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING;
      assertFalse(auction.isAcceptingBids(START.minusNanos(1)));
      assertEquals(live, auction.isAcceptingBids(START));
      assertEquals(live, auction.isAcceptingBids(END.minusNanos(1)));
      assertFalse(auction.isAcceptingBids(END));
      assertEquals(110_000, auction.minimumBidCents());
      assertEquals(status, auction.getStatus());
      assertEquals("Auction #1 " + status, auction.printInfo());
    }
  }

  @Test
  void auctionsRejectInvalidSnapshotsAndNeverWrapMoneyOnOverflow() {
    assertThrows(IllegalArgumentException.class, () -> new Auction(0, AuctionStatus.OPEN, START, END, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Auction(1, AuctionStatus.OPEN, END, START, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Auction(1, AuctionStatus.OPEN, START, END, -1, 1));
    assertThrows(IllegalArgumentException.class, () -> new Auction(1, AuctionStatus.OPEN, START, END, 1, 0));
    assertThrows(NullPointerException.class, () -> new Auction(1, null, START, END, 1, 1));
    Auction capped = new Auction(1, AuctionStatus.RUNNING, START, END, Long.MAX_VALUE, 1);
    assertThrows(ArithmeticException.class, capped::minimumBidCents);
  }

  @Test
  void acceptedTransactionsPreserveHistoryAndExposeEntityBehavior() {
    BidDto dto = new BidDto(5, 1, 2, 110_000, START, BidStatus.ACTIVE, "bid-5");
    BidTransaction transaction = new BidTransaction(dto);
    assertEquals(5, transaction.getId());
    assertEquals(dto, transaction.toDto());
    Entity entity = transaction;
    assertEquals("Bid #5: 110000 cents", entity.printInfo());
    assertThrows(NullPointerException.class, () -> new BidTransaction(null));
  }
}
