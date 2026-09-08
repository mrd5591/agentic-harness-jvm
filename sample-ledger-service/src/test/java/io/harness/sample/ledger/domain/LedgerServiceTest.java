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

/** The invariant is tested as a property over many amounts, not as one example. */
class LedgerServiceTest {

  private final LedgerService ledger = new LedgerService();

  @ParameterizedTest
  @ValueSource(longs = {1L, 2L, 99L, 100L, 4200L, 999_999_999L, Long.MAX_VALUE})
  @DisplayName("every posting balances and moves the event amount, whatever the amount")
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

    assertThatThrownBy(() -> LedgerService.requireBalanced(unbalanced))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting does not balance, net = 40");
  }

  @Test
  @DisplayName("two entries on the same side do not cancel")
  void sameSideEntriesDoNotCancel() {
    List<EntryResponse> bothDebits =
        List.of(new EntryResponse("a", Side.DEBIT, 100L), new EntryResponse("b", Side.DEBIT, 100L));

    assertThatThrownBy(() -> LedgerService.requireBalanced(bothDebits))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting does not balance, net = 200");
  }

  @Test
  @DisplayName("debits that wrap a long to zero are refused, not accepted as balanced")
  void overflowingDebitsAreRefused() {
    List<EntryResponse> wrapsToZero =
        List.of(
            new EntryResponse("a", Side.DEBIT, Long.MAX_VALUE),
            new EntryResponse("b", Side.DEBIT, Long.MAX_VALUE),
            new EntryResponse("c", Side.DEBIT, 2L));

    assertThatThrownBy(() -> LedgerService.requireBalanced(wrapsToZero))
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("long overflow");
  }

  @Test
  @DisplayName("credits that wrap a long are refused, not accepted as balanced")
  void overflowingCreditsAreRefused() {
    List<EntryResponse> wraps =
        List.of(
            new EntryResponse("a", Side.CREDIT, Long.MAX_VALUE),
            new EntryResponse("b", Side.CREDIT, Long.MAX_VALUE));

    assertThatThrownBy(() -> LedgerService.requireBalanced(wraps))
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("long overflow");
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
  @DisplayName("post rejects a balanced posting that moves a different amount than the event")
  void postRejectsWrongAmount() {
    LedgerService pennies =
        new LedgerService(
            event ->
                List.of(
                    new EntryResponse("a", Side.DEBIT, 1L),
                    new EntryResponse("b", Side.CREDIT, 1L)));

    assertThatThrownBy(() -> pennies.post(new OrderPlacedEvent("order-6", 100L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting moves 1 but the event is for 100");
  }

  @Test
  @DisplayName("post rejects a strategy that moves no money at all")
  void postRejectsEmptyEntries() {
    LedgerService silent = new LedgerService(event -> List.of());

    assertThatThrownBy(() -> silent.post(new OrderPlacedEvent("order-7", 10L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting moves 0 but the event is for 10");
  }

  @Test
  @DisplayName("post rejects balanced negative entries, which move the wrong amount")
  void postRejectsNegativeEntries() {
    LedgerService negative =
        new LedgerService(
            event ->
                List.of(
                    new EntryResponse("a", Side.DEBIT, -50L),
                    new EntryResponse("b", Side.CREDIT, -50L)));

    assertThatThrownBy(() -> negative.post(new OrderPlacedEvent("order-8", 50L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting moves -50 but the event is for 50");
  }

  @Test
  @DisplayName("the amount check refuses debits that overflow, even when the posting balances")
  void amountCheckRefusesOverflow() {
    List<EntryResponse> balancedButHuge =
        List.of(
            new EntryResponse("a", Side.DEBIT, Long.MAX_VALUE),
            new EntryResponse("b", Side.CREDIT, Long.MAX_VALUE),
            new EntryResponse("c", Side.DEBIT, Long.MAX_VALUE),
            new EntryResponse("d", Side.CREDIT, Long.MAX_VALUE));

    assertThatCode(() -> LedgerService.requireBalanced(balancedButHuge)).doesNotThrowAnyException();
    assertThatThrownBy(() -> LedgerService.requireAmount(balancedButHuge, Long.MAX_VALUE))
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("long overflow");
  }

  @Test
  @DisplayName("the amount check counts debits only, so credits cannot stand in for them")
  void amountCheckCountsDebitsOnly() {
    List<EntryResponse> lopsided =
        List.of(new EntryResponse("a", Side.DEBIT, 100L), new EntryResponse("b", Side.CREDIT, 60L));

    assertThatCode(() -> LedgerService.requireAmount(lopsided, 100L)).doesNotThrowAnyException();
    assertThatThrownBy(() -> LedgerService.requireAmount(lopsided, 60L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting moves 100 but the event is for 60");
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
  @DisplayName("the balance check alone accepts an empty set; post does not")
  void emptyEntriesBalance() {
    assertThatCode(() -> LedgerService.requireBalanced(List.of())).doesNotThrowAnyException();
    assertThatThrownBy(() -> LedgerService.requireAmount(List.of(), 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("posting moves 0 but the event is for 1");
  }
}
