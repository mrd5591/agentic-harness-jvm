package io.harness.sample.ledger.contract;

import java.util.List;
import java.util.Objects;

/** The wire contract for the ledger service. Money is in minor units. */
public interface LedgerContracts {

  enum Side {
    DEBIT,
    CREDIT
  }

  /**
   * One side of a posting.
   *
   * <p>The amount carries magnitude only; direction is {@link Side}'s job. Encoding direction a
   * second time in the sign is not redundancy, it is a hole: a negative debit offsets an oversized
   * one, so entries that move twice the event's money still satisfy both ledger guards. A null side
   * is the same hole from the other end, since every check that asks {@code side() == DEBIT} files
   * a null as a credit. Neither is a value, so neither is constructible.
   */
  record EntryResponse(String account, Side side, long amountCents) {

    public EntryResponse {
      Objects.requireNonNull(account, "account must not be null");
      Objects.requireNonNull(side, "side must not be null");
      if (amountCents <= 0) {
        throw new IllegalArgumentException("amountCents must be positive, was " + amountCents);
      }
    }
  }

  record PostingResponse(String sourceId, List<EntryResponse> entries) {

    public PostingResponse {
      entries = List.copyOf(entries);
    }
  }
}
