package io.harness.sample.ledger.contract;

import java.util.List;

/** The wire contract for the ledger service. */
public interface LedgerContracts {

  /** Which side of the book an entry sits on. */
  enum Side {
    /** An increase to a debit-normal account. */
    DEBIT,
    /** An increase to a credit-normal account. */
    CREDIT
  }

  /**
   * One line of a posting.
   *
   * @param account the account name
   * @param side which side of the book
   * @param amountCents the amount in minor units, always positive
   */
  record EntryResponse(String account, Side side, long amountCents) {}

  /**
   * A balanced set of entries derived from one source event.
   *
   * <p>The compact constructor copies the list. A record with a mutable component is not actually a
   * value: the caller keeps a reference and can rewrite the posting after it was validated. The
   * static-analysis gate flagged exactly this on the first build, which is the sort of find that
   * pays for the gate.
   *
   * @param sourceId the identifier of the event that caused this posting
   * @param entries the lines, which must sum to zero across sides
   */
  record PostingResponse(String sourceId, List<EntryResponse> entries) {

    /** Defensively copy the entries so the posting cannot be mutated after validation. */
    public PostingResponse {
      entries = List.copyOf(entries);
    }
  }
}
