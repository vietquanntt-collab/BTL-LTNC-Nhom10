package com.btl.team10.server.domain;

import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;
import java.time.Instant;
import java.util.Objects;

/** Immutable item model. Its subtype supplies the catalog type. */
public abstract class Item extends Entity {
  private final long sellerId;
  private final String name;
  private final String description;
  private final String imageBase64;
  private final Instant createdAt;
  private final Instant updatedAt;

  protected Item(long id, long sellerId, String name, String description, String imageBase64,
      Instant createdAt, Instant updatedAt) {
    super(id);
    if (sellerId <= 0 || name == null || name.isBlank() || name.trim().length() > 200) {
      throw new IllegalArgumentException("invalid item");
    }
    this.sellerId = sellerId;
    this.name = name.trim();
    this.description = description == null ? "" : description.trim();
    this.imageBase64 = imageBase64 == null || imageBase64.isBlank() ? null : imageBase64;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public final long getSellerId() {
    return sellerId;
  }

  public final String getName() {
    return name;
  }

  public final String getDescription() {
    return description;
  }

  public final String getImageBase64() {
    return imageBase64;
  }

  public abstract ItemType getType();

  public abstract String getCategoryLabel();

  /** Converts a persisted item; draft id zero is not a valid wire DTO. */
  public final ItemDto toDto() {
    return new ItemDto(getId(), sellerId, name, description, getType(), imageBase64,
        createdAt, updatedAt);
  }

  @Override
  public final String printInfo() {
    return getCategoryLabel() + ": " + name;
  }
}
