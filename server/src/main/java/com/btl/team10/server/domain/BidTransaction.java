package com.btl.team10.server.domain;

import com.btl.team10.common.dto.BidDto;
import java.util.Objects;

/** Accepted transaction loaded for bid history and socket responses. */
public final class BidTransaction extends Entity {
  private final BidDto data;

  public BidTransaction(BidDto data) {
    super(Objects.requireNonNull(data, "bid").bidId());
    this.data = data;
  }

  public BidDto toDto() {
    return data;
  }

  @Override
  public String printInfo() {
    return "Bid #" + getId() + ": " + data.amountCents() + " cents";
  }
}
