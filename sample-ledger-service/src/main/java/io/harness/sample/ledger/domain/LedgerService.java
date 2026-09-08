package io.harness.sample.ledger.domain;

import io.harness.sample.ledger.contract.LedgerContracts.EntryResponse;
import io.harness.sample.ledger.contract.LedgerContracts.PostingResponse;
import io.harness.sample.ledger.contract.LedgerContracts.Side;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import java.util.List;

/**
 * Turns an order event into a balanced posting.
 *
 * <p>The strategy decides which accounts move; {@link #requireBalanced} checks the value it
 * returned. Changing the strategy cannot produce an unbalanced posting.
 */
public class LedgerService {

  public static final String ACCOUNTS_RECEIVABLE = "accounts-receivable";

  public static final String REVENUE = "revenue";

  private final EntryStrategy strategy;

  public LedgerService() {
    this(LedgerService::saleEntries);
  }

  public LedgerService(EntryStrategy strategy) {
    this.strategy = strategy;
  }

  /** Decides which accounts move for a given event. */
  @FunctionalInterface
  public interface EntryStrategy {

    List<EntryResponse> entriesFor(OrderPlacedEvent event);
  }

  /** The standard sale posting: receivable up, revenue up. */
  public static List<EntryResponse> saleEntries(OrderPlacedEvent event) {
    return List.of(
        new EntryResponse(ACCOUNTS_RECEIVABLE, Side.DEBIT, event.totalCents()),
        new EntryResponse(REVENUE, Side.CREDIT, event.totalCents()));
  }

  /**
   * Post an order to the book.
   *
   * @throws IllegalArgumentException when the event amount is not positive
   * @throws IllegalStateException when the strategy produced entries that do not balance
   */
  public PostingResponse post(OrderPlacedEvent event) {
    if (event.totalCents() <= 0) {
      throw new IllegalArgumentException("totalCents must be positive");
    }
    List<EntryResponse> entries = strategy.entriesFor(event);
    requireBalanced(entries);
    return new PostingResponse(event.orderId(), entries);
  }

  /**
   * The invariant.
   *
   * @throws IllegalStateException when debits do not equal credits
   */
  public static void requireBalanced(List<EntryResponse> entries) {
    long net = 0;
    for (EntryResponse entry : entries) {
      net += entry.side() == Side.DEBIT ? entry.amountCents() : -entry.amountCents();
    }
    if (net != 0) {
      throw new IllegalStateException("posting does not balance, net = " + net);
    }
  }
}
