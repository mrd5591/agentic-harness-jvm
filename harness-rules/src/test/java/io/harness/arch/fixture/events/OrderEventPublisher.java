package io.harness.arch.fixture.events;

import io.harness.arch.fixture.Fixtures;
import java.util.List;

/** Event publishers for the parameter rule's tests. */
public final class OrderEventPublisher {

  private OrderEventPublisher() {}

  public static void publishClean(Fixtures.OrderResponse payload) {
    // no-op fixture
  }

  public static void publishLeaky(List<Fixtures.OrderEntity> payload) {
    // no-op fixture
  }
}
