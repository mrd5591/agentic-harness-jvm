package io.harness.sample.order.events;

import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;

/** Publishes order events. Takes a contract record, never the entity. */
@FunctionalInterface
public interface OrderEventPublisher {

  void publish(OrderPlacedEvent event);
}
