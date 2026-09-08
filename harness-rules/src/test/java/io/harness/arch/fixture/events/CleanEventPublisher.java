package io.harness.arch.fixture.events;

import io.harness.arch.fixture.contract.OrderResponse;

/** Clean fixture: a publisher that takes the contract record. Importable on its own. */
public interface CleanEventPublisher {

  static void publish(OrderResponse payload) {
    // no-op fixture
  }
}
