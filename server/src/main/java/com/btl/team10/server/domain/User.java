package com.btl.team10.server.domain;

import com.btl.team10.common.dto.UserDto;
import com.btl.team10.common.model.Role;
import java.util.Objects;

/** Role-specific authorization policy used by server routes. */
public abstract class User extends Entity {
  private final String account;

  protected User(long id, String account) {
    super(id);
    if (id == 0 || account == null || account.isBlank()) {
      throw new IllegalArgumentException("persisted user required");
    }
    this.account = account;
  }

  public final String getAccount() {
    return account;
  }

  public abstract Role getRole();

  public boolean canBid() {
    return false;
  }

  public boolean canCreateItem() {
    return false;
  }

  public abstract boolean canManage(long ownerId);

  @Override
  public final String printInfo() {
    return getRole() + " @" + account;
  }

  public static User from(UserDto dto) {
    Objects.requireNonNull(dto, "user");
    return of(dto.userId(), dto.account(), dto.role());
  }

  public static User of(long id, String account, Role role) {
    return switch (Objects.requireNonNull(role, "role")) {
      case BIDDER -> new Bidder(id, account);
      case SELLER -> new Seller(id, account);
      case ADMIN -> new Admin(id, account);
    };
  }
}
