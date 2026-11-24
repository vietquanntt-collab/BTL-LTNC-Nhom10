package com.btl.team10.server.domain;

import com.btl.team10.common.model.AuctionStatus;
import java.time.Instant;
import java.util.Objects;

/** Rule snapshot. Transactions and version updates belong to the service. */
public final class Auction extends Entity {
  private final AuctionStatus status;
  private final Instant startAt;
  private final Instant endAt;
  private final long currentPriceCents;
  private final long minIncrementCents;

  public Auction(long id, AuctionStatus status, Instant startAt, Instant endAt,
      long currentPriceCents, long minIncrementCents) {
    super(id);
    this.status = Objects.requireNonNull(status, "status");
    this.startAt = Objects.requireNonNull(startAt, "startAt");
    this.endAt = Objects.requireNonNull(endAt, "endAt");
    if (id == 0 || !endAt.isAfter(startAt) || currentPriceCents < 0 || minIncrementCents <= 0) {
      throw new IllegalArgumentException("invalid auction");
    }
    this.currentPriceCents = currentPriceCents;
    this.minIncrementCents = minIncrementCents;
  }

  public long minimumBidCents() {
    return Math.addExact(currentPriceCents, minIncrementCents);
  }

  public boolean isAcceptingBids(Instant now) {
    return (status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING)
        && !now.isBefore(startAt) && now.isBefore(endAt);
  }

  public AuctionStatus getStatus() {
    return status;
  }

  @Override
  public String printInfo() {
    return "Auction #" + getId() + " " + status;
  }
}
