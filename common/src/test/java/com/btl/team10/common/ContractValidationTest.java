package com.btl.team10.common;

import com.btl.team10.common.dto.*;
import com.btl.team10.common.exception.*;
import com.btl.team10.common.model.*;
import com.btl.team10.common.protocol.*;
import com.btl.team10.common.serialization.JsonCodec;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContractValidationTest {
  private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

  @Test void catalogUserAndPaymentSnapshotsPreserveMoneyAndImageThroughJson() {
    var item = new ItemDto(1,2,"Card","Description",ItemType.CARD,"YWJj",NOW,NOW);
    var user = new UserDto(3,"bidder",Role.BIDDER,200000,50000,NOW,NOW);
    var wallet = new WalletDto(3,200000,50000,2);
    var settlement = new SettlementDto(4,1,3L,2,50000,"settle",NOW);
    assertEquals(item, JsonCodec.fromJson(JsonCodec.toJson(item),ItemDto.class));
    assertEquals(user, JsonCodec.fromJson(JsonCodec.toJson(user),UserDto.class));
    assertEquals(wallet, JsonCodec.fromJson(JsonCodec.toJson(wallet),WalletDto.class));
    assertEquals(settlement, JsonCodec.fromJson(JsonCodec.toJson(settlement),SettlementDto.class));
    assertFalse(new SettlementWriteResult(settlement,false).created());
    assertEquals(settlement, new SettlementWriteResult(settlement,true).settlement());
    assertThrows(IllegalArgumentException.class,()->new SettlementWriteResult(null,false));
  }

  @Test void itemCommandsNormalizeDescriptionAndRejectInvalidCatalogValues() {
    assertEquals("", new CreateItemCommand(1,"ok",null,ItemType.FIGURE,null).description());
    assertEquals("detail", new CreateItemCommand(1,"ok"," detail ",ItemType.POSTER,null).description());
    assertAll(
      ()->assertThrows(ValidationException.class,()->new CreateItemCommand(0,"ok","",ItemType.CARD,null)),
      ()->assertThrows(ValidationException.class,()->new CreateItemCommand(1," ","",ItemType.CARD,null)),
      ()->assertThrows(ValidationException.class,()->new CreateItemCommand(1,"x".repeat(201),"",ItemType.CARD,null)),
      ()->assertThrows(ValidationException.class,()->new CreateItemCommand(1,"ok","",null,null)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(0,1,"ok","",ItemType.CARD,null,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(1,0,"ok","",ItemType.CARD,null,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(1,1,null,"",ItemType.CARD,null,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(1,1,"ok",null,ItemType.CARD,null,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(1,1,"ok","",null,null,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new ItemDto(1,1,"ok","",ItemType.CARD,null,null,NOW)));
  }

  @Test void userAndWalletSnapshotsRejectImpossibleBalancesAndIdentity() {
    assertAll(
      ()->assertThrows(ValidationException.class,()->new UserDto(0,"a",Role.ADMIN,0,0,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new UserDto(1," ",Role.ADMIN,0,0,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new UserDto(1,"a",null,0,0,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new UserDto(1,"a",Role.SELLER,-1,0,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new UserDto(1,"a",Role.BIDDER,1,2,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new UserDto(1,"a",Role.BIDDER,1,0,NOW,null)),
      ()->assertThrows(ValidationException.class,()->new WalletDto(0,0,0,0)),
      ()->assertThrows(ValidationException.class,()->new WalletDto(1,-1,0,0)),
      ()->assertThrows(ValidationException.class,()->new WalletDto(1,1,-1,0)),
      ()->assertThrows(ValidationException.class,()->new WalletDto(1,1,2,0)),
      ()->assertThrows(ValidationException.class,()->new WalletDto(1,1,0,-1)));
    var error=assertThrows(ValidationException.class,()->new WalletDto(1,1,2,0));
    assertEquals("frozenCents",error.field()); assertEquals(ErrorCode.VALIDATION_ERROR,error.errorCode());
  }

  @Test void auctionCommandRejectsInvalidMoneyAndReversedTime() {
    assertEquals(0,new CreateAuctionCommand(1,0,1,NOW,NOW.plusSeconds(1)).startPriceCents());
    assertAll(
      ()->assertThrows(ValidationException.class,()->new CreateAuctionCommand(0,0,1,NOW,NOW.plusSeconds(1))),
      ()->assertThrows(ValidationException.class,()->new CreateAuctionCommand(1,-1,1,NOW,NOW.plusSeconds(1))),
      ()->assertThrows(ValidationException.class,()->new CreateAuctionCommand(1,0,0,NOW,NOW.plusSeconds(1))),
      ()->assertThrows(ValidationException.class,()->new CreateAuctionCommand(1,0,1,NOW,NOW)),
      ()->assertThrows(ValidationException.class,()->new CreateAuctionCommand(1,0,1,null,NOW)));
  }

  @Test void settlementRequiresConsistentWinnerAmountAndStableReceipt() {
    assertNull(new SettlementDto(1,2,null,3,0,"empty",NOW).winnerId());
    assertAll(
      ()->assertThrows(ValidationException.class,()->new SettlementDto(0,2,null,3,0,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,0,null,3,0,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,0L,3,1,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,null,0,0,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,null,3,-1,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,null,3,1,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,4L,3,0,"r",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,null,3,0," ",NOW)),
      ()->assertThrows(ValidationException.class,()->new SettlementDto(1,2,null,3,0,"r",null)));
  }

  @Test void malformedJsonFailsAtSerializationBoundary() {
    assertThrows(com.google.gson.JsonParseException.class,()->JsonCodec.fromJson("not-json",WalletDto.class));
    String badInstant=JsonCodec.toJson("not-an-instant");
    assertThrows(com.google.gson.JsonParseException.class,()->JsonCodec.fromJson(badInstant,Instant.class));
    java.lang.reflect.Type listType=new com.google.gson.reflect.TypeToken<List<WalletDto>>(){}.getType();
    assertThrows(com.google.gson.JsonParseException.class,()->JsonCodec.fromJson(" ",listType));
    var list=List.of(new WalletDto(1,100,20,1));
    assertEquals(list,JsonCodec.fromJson(JsonCodec.toJson(list),listType));
    var rejection=new BidRejectedException(ErrorCode.BID_TOO_LOW,"below minimum");
    assertEquals(ErrorCode.BID_TOO_LOW,rejection.errorCode());
  }
}
