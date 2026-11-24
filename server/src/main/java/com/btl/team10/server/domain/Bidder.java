package com.btl.team10.server.domain;

import com.btl.team10.common.model.Role;

public final class Bidder extends User {
  public Bidder(long id, String account) {
    super(id, account);
  }

  @Override
  public Role getRole() {
    return Role.BIDDER;
  }

  @Override
  public boolean canBid() {
    return true;
  }

  @Override
  public boolean canManage(long ownerId) {
    return false;
  }
}
