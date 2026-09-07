package io.harness.sample.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.sample.ledger.contract.LedgerContracts.EntryResponse;
import io.harness.sample.ledger.contract.LedgerContracts.PostingResponse;
import io.harness.sample.ledger.contract.LedgerContracts.Side;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The invariant is tested as a property over many amounts, not as one example.
 *
 * <p>Example-based tests are what an agent writes when asked to make coverage go up. They pass
 * while asserting almost nothing. A property over a range is the cheapest available upgrade, and it
 * is what makes the mutation score survive.
 */
class LedgerServiceTest {

  private final LedgerService ledger = new LedgerService();

  @ParameterizedTest
  @ValueSource(longs = {1L, 2L, 99L, 100L, 4200L, 999_999_999L})
  @DisplayName("every posting balances, whatever the amount")
  void everyPostingBalances(long amount) {
    PostingResponse posting = ledger.post(new OrderPlacedEvent("order-1", amount));

    long debits =
        posting.entries().stream()
            .filter(e -> e.side() == Side.DEBIT)
            .mapToLong(EntryResponse::amountCents)
            .sum();
    long credits =
        posting.entries().stream()
            .filter(e -> e.side() == Side.CREDIT)
            .mapToLong(EntryResponse::amountCents)
            .sum();

    assertThat(debits).isEqualTo(credits).isEqualTo(amount);
    assertThat(posting.sourceId()).isEqualTo("order-1");
    assertThat(posting.entries()).hasSize(2);
  }

  @Test
  @DisplayName("the posting names the two conventional accounts")
  void postingNamesBothAccounts() {
    PostingResponse posting = ledger.post(new OrderPlacedEvent("order-2", 500L));

    assertThat(posting.entries())
        .extracting(EntryResponse::account)
        .containsExactly(LedgerService.ACCOUNTS_RECEIVABLE, LedgerService.REVENUE);
  }

  @Test
  @DisplayName("a non-positive amount is rejected")
  void nonPositiveAmountIsRejected() {
    assertThatThrownBy(() -> ledger.post(new OrderPlacedEvent("order-3", 0L)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("totalCents must be positive");
  }

  @Test
  @DisplayName("the balance check rejects an unbalanced set and reports the signed net")
  void unbalancedEntriesAreRejected() {
    List<EntryResponse> unbalanced =
        List.of(new EntryResponse("a", Side.DEBIT, 100L), new EntryResponse("b", Side.CREDIT, 60L));

    // The exact message matters. Asserting only that it contains "40" also passes when the
    // arithmetic is inverted and the real net is -40, which is how a sign bug survives a suite.
    assertThatThrownBy(() -> LedgerService.requireBalanced(unbalanced))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting does not balance, net = 40");
  }

  @Test
  @DisplayName("two entries on the same side do not cancel")
  void sameSideEntriesDoNotCancel() {
    List<EntryResponse> bothDebits =
        List.of(new EntryResponse("a", Side.DEBIT, 100L), new EntryResponse("b", Side.DEBIT, 100L));

    // Symmetric fixtures hide sign errors: with one debit and one credit, swapping the two still
    // nets to zero. This case only balances if DEBIT and CREDIT are treated differently.
    assertThatThrownBy(() -> LedgerService.requireBalanced(bothDebits))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting does not balance, net = 200");
  }

  @Test
  @DisplayName("post rejects a strategy that produces an unbalanced posting")
  void postRejectsUnbalancedStrategy() {
    LedgerService broken =
        new LedgerService(event -> List.of(new EntryResponse("only-one-side", Side.DEBIT, 10L)));

    assertThatThrownBy(() -> broken.post(new OrderPlacedEvent("order-4", 10L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting does not balance, net = 10");
  }

  @Test
  @DisplayName("the standard strategy is the one used by default")
  void defaultStrategyIsTheSaleposting() {
    List<EntryResponse> entries = LedgerService.saleEntries(new OrderPlacedEvent("order-5", 250L));

    assertThat(entries)
        .containsExactly(
            new EntryResponse(LedgerService.ACCOUNTS_RECEIVABLE, Side.DEBIT, 250L),
            new EntryResponse(LedgerService.REVENUE, Side.CREDIT, 250L));
  }

  @Test
  @DisplayName("the balance check accepts an empty set")
  void emptyEntriesBalance() {
    assertThatCode(() -> LedgerService.requireBalanced(List.of())).doesNotThrowAnyException();
  }
}
