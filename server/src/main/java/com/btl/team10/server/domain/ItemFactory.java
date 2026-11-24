package com.btl.team10.server.domain;

import com.btl.team10.common.dto.CreateItemCommand;
import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Factory Method: each concrete creator constructs its matching Item subtype. */
public abstract class ItemFactory {
  private static final Map<ItemType, ItemFactory> CREATORS = Map.of(
      ItemType.CARD, new CardFactory(),
      ItemType.FIGURE, new FigureFactory(),
      ItemType.POSTER, new PosterFactory());

  protected abstract Item createItem(long id, long sellerId, String name, String description,
      String image, Instant created, Instant updated);

  public final Item createDraft(CreateItemCommand command) {
    Objects.requireNonNull(command, "command");
    if (forType(command.itemType()) != this) {
      throw new IllegalArgumentException("wrong item creator");
    }
    Instant now = Instant.now();
    return createItem(0, command.sellerId(), command.name(), command.description(),
        command.imageBase64(), now, now);
  }

  public final Item restore(ItemDto data) {
    Objects.requireNonNull(data, "item");
    Item item = createItem(data.itemId(), data.sellerId(), data.name(), data.description(),
        data.imageBase64(), data.createdAt(), data.updatedAt());
    if (item.getType() != data.itemType()) {
      throw new IllegalArgumentException("wrong item creator");
    }
    return item;
  }

  public static ItemFactory forType(ItemType type) {
    return CREATORS.get(Objects.requireNonNull(type, "type"));
  }

  public static Item from(ItemDto data) {
    Objects.requireNonNull(data, "item");
    return forType(data.itemType()).restore(data);
  }

  private static final class CardFactory extends ItemFactory {
    @Override
    protected Item createItem(long id, long sellerId, String name, String description,
        String image, Instant created, Instant updated) {
      return new Card(id, sellerId, name, description, image, created, updated);
    }
  }

  private static final class FigureFactory extends ItemFactory {
    @Override
    protected Item createItem(long id, long sellerId, String name, String description,
        String image, Instant created, Instant updated) {
      return new Figure(id, sellerId, name, description, image, created, updated);
    }
  }

  private static final class PosterFactory extends ItemFactory {
    @Override
    protected Item createItem(long id, long sellerId, String name, String description,
        String image, Instant created, Instant updated) {
      return new Poster(id, sellerId, name, description, image, created, updated);
    }
  }
}
