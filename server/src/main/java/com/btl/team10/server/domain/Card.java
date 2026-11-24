package com.btl.team10.server.domain;

import com.btl.team10.common.model.ItemType;
import java.time.Instant;

public final class Card extends Item {
  public Card(long id, long sellerId, String name, String description, String image,
      Instant created, Instant updated) {
    super(id, sellerId, name, description, image, created, updated);
  }

  @Override
  public ItemType getType() {
    return ItemType.CARD;
  }

  @Override
  public String getCategoryLabel() {
    return "Thẻ sưu tầm";
  }
}
