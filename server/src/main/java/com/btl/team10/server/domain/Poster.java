package com.btl.team10.server.domain;

import com.btl.team10.common.model.ItemType;
import java.time.Instant;

public final class Poster extends Item {
  public Poster(long id, long sellerId, String name, String description, String image,
      Instant created, Instant updated) {
    super(id, sellerId, name, description, image, created, updated);
  }

  @Override
  public ItemType getType() {
    return ItemType.POSTER;
  }

  @Override
  public String getCategoryLabel() {
    return "Ấn phẩm sưu tầm";
  }
}
