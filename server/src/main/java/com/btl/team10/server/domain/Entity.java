package com.btl.team10.server.domain;

/** Identity for a persisted entity or a draft with id zero. */
public abstract class Entity {
  private final long id;

  protected Entity(long id) {
    if (id < 0) {
      throw new IllegalArgumentException("id must not be negative");
    }
    this.id = id;
  }

  public final long getId() {
    return id;
  }

  public abstract String printInfo();
}
