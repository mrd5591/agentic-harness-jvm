package io.harness.sample.ledger.contract;

import java.util.List;

/** The wire contract for the ledger service. Money is in minor units. */
public interface LedgerContracts {

  enum Side {
    DEBIT,
    CREDIT
  }

  record EntryResponse(String account, Side side, long amountCents) {}

  record PostingResponse(String sourceId, List<EntryResponse> entries) {

    public PostingResponse {
      entries = List.copyOf(entries);
    }
  }
}
