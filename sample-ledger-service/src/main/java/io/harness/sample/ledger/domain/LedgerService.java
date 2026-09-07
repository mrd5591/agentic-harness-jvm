package io.harness.sample.ledger.domain;

import io.harness.sample.ledger.contract.LedgerContracts.EntryResponse;
import io.harness.sample.ledger.contract.LedgerContracts.PostingResponse;
import io.harness.sample.ledger.contract.LedgerContracts.Side;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import java.util.List;

/**
 * Turns an order event into a balanced posting.
 *
 * <p>This class exists to carry one invariant: a posting balances. Note the shape. The rule that
 * decides which accounts move is a strategy, and the balance check is applied to whatever that
 * strategy returns. An agent can rewrite the strategy however it likes and cannot get an unbalanced
 * posting past {@link #post}, because the check is on the value rather than on the code that
 * produced it.
 *
 * <p>That seam was not in the first draft. Mutation testing pointed out that deleting the balance
 * check entirely left every test green, because the only strategy in the codebase could not produce
 * an unbalanced result. A guard no test can trip is decoration. See README, "What mutation testing
 * found".
 */
public class LedgerService {

  /** The account that receives the debit for a sale. */
  public static final String ACCOUNTS_RECEIVABLE = "accounts-receivable";

  /** The account that receives the credit for a sale. */
  public static final String REVENUE = "revenue";

  private final EntryStrategy strategy;

  /** Use the standard two-line sale posting. */
  public LedgerService() {
    this(LedgerService::saleEntries);
  }

  /**
   * Use a supplied posting strategy.
   *
   * @param strategy decides which accounts move and by how much
   */
  public LedgerService(EntryStrategy strategy) {
    this.strategy = strategy;
  }

  /** Decides which accounts move for a given event. */
  @FunctionalInterface
  public interface EntryStrategy {

    /**
     * Produce the lines for an event.
     *
     * @param event the source event
     * @return the lines, which the caller will validate
     */
    List<EntryResponse> entriesFor(OrderPlacedEvent event);
  }

  /**
   * The standard sale posting: receivable up, revenue up.
   *
   * @param event the source event
   * @return two balanced lines
   */
  public static List<EntryResponse> saleEntries(OrderPlacedEvent event) {
    return List.of(
        new EntryResponse(ACCOUNTS_RECEIVABLE, Side.DEBIT, event.totalCents()),
        new EntryResponse(REVENUE, Side.CREDIT, event.totalCents()));
  }

  /**
   * Post an order to the book.
   *
   * @param event the placed-order event
   * @return the balanced posting
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
   * The invariant, in one place, applied to the value rather than trusted to the caller.
   *
   * @param entries the lines to check
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
