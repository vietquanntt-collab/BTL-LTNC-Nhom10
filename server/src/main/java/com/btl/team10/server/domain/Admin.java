package com.btl.team10.server.domain;

import com.btl.team10.common.model.Role;

public final class Admin extends User {
  public Admin(long id, String account) {
    super(id, account);
  }

  @Override
  public Role getRole() {
    return Role.ADMIN;
  }

  @Override
  public boolean canManage(long ownerId) {
    return true;
  }
}
