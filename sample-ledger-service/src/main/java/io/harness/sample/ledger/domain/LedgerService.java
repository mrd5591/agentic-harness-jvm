package io.harness.sample.ledger.domain;

import io.harness.sample.ledger.contract.LedgerContracts.EntryResponse;
import io.harness.sample.ledger.contract.LedgerContracts.PostingResponse;
import io.harness.sample.ledger.contract.LedgerContracts.Side;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import java.util.List;

/**
 * Turns an order event into a balanced posting.
 *
 * <p>The strategy decides which accounts move; {@link #requireBalanced} and {@link #requireAmount}
 * check the immutable copy that ships, taken before either guard runs. Changing the strategy cannot
 * produce a posting that is unbalanced, or that moves a different amount than the event carries.
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
   * @throws IllegalArgumentException when the event amount is not positive, or when the strategy
   *     built an entry that is not a value
   * @throws IllegalStateException when the strategy produced entries that do not balance, or that
   *     move a different amount than the event
   * @throws ArithmeticException when the entries overflow a long, which is refused rather than
   *     wrapped
   * @throws NullPointerException when the strategy returned null, or a list holding a null entry
   */
  public PostingResponse post(OrderPlacedEvent event) {
    if (event.totalCents() <= 0) {
      throw new IllegalArgumentException("totalCents must be positive");
    }
    // Copy first, then check the copy. List.copyOf re-reads its argument through toArray(), so
    // validating the strategy's own list and copying it afterwards checks one reading and ships
    // another: a list that answers twice, or is mutated in between, passes both guards unchecked.
    List<EntryResponse> entries = List.copyOf(strategy.entriesFor(event));
    requireBalanced(entries);
    requireAmount(entries, event.totalCents());
    return new PostingResponse(event.orderId(), entries);
  }

  /**
   * The invariant. Sums are exact: an overflow throws instead of wrapping to a false zero.
   *
   * @throws IllegalStateException when debits do not equal credits
   */
  public static void requireBalanced(List<EntryResponse> entries) {
    long net = 0;
    for (EntryResponse entry : entries) {
      net =
          entry.side() == Side.DEBIT
              ? Math.addExact(net, entry.amountCents())
              : Math.subtractExact(net, entry.amountCents());
    }
    if (net != 0) {
      throw new IllegalStateException("posting does not balance, net = " + net);
    }
  }

  /**
   * A balanced posting that moves the wrong amount, or none, is not a booking of this event.
   *
   * <p>Debits are summed unsigned because {@link EntryResponse} refuses a non-positive amount. A
   * second guard here would be unreachable, and the coverage floor is right to forbid it.
   *
   * @throws IllegalStateException when the debits do not add up to the amount
   */
  public static void requireAmount(List<EntryResponse> entries, long amountCents) {
    long debits = 0;
    for (EntryResponse entry : entries) {
      if (entry.side() == Side.DEBIT) {
        debits = Math.addExact(debits, entry.amountCents());
      }
    }
    if (debits != amountCents) {
      throw new IllegalStateException(
          "posting moves " + debits + " but the event is for " + amountCents);
    }
  }
}
