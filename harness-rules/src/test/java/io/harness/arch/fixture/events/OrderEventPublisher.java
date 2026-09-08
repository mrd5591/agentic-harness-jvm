package io.harness.arch.fixture.events;

import io.harness.arch.fixture.Fixtures;
import java.util.List;
import java.util.function.Consumer;

/** Violation fixture: publishers that let an entity onto the wire. */
public interface OrderEventPublisher {

  static void publishLeaky(List<Fixtures.OrderEntity> payload) {
    // no-op fixture
  }

  static void publishThroughLowerBound(Consumer<? super Fixtures.OrderEntity> sink) {
    // no-op fixture
  }
}
