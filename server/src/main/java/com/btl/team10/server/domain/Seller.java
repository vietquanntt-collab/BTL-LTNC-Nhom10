package com.btl.team10.server.domain;

import com.btl.team10.common.model.Role;

public final class Seller extends User {
  public Seller(long id, String account) {
    super(id, account);
  }

  @Override
  public Role getRole() {
    return Role.SELLER;
  }

  @Override
  public boolean canCreateItem() {
    return true;
  }

  @Override
  public boolean canManage(long ownerId) {
    return ownerId == getId();
  }
}
