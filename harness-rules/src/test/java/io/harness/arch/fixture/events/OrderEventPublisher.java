package io.harness.arch.fixture.events;

import io.harness.arch.fixture.Fixtures;
import java.util.List;

/**
 * Event publishers used by the parameter rule's tests. Lives in an {@code ..events..} package so
 * the package-matched rule selects it.
 */
public final class OrderEventPublisher {

  private OrderEventPublisher() {}

  /** Clean: publishes a record. */
  public static void publishClean(Fixtures.OrderResponse payload) {
    // no-op fixture
  }

  /** Violation: accepts an entity wrapped in a collection. */
  public static void publishLeaky(List<Fixtures.OrderEntity> payload) {
    // no-op fixture
  }
}
