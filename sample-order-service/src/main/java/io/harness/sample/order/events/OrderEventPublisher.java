package io.harness.sample.order.events;

import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;

/**
 * Publishes order events.
 *
 * <p>The parameter type is a record from the contract package, not the entity. An event payload is
 * serialized with no compile-time contract on the far side, so the signature here is the only place
 * the shape can be pinned down, and a rule enforces it.
 */
@FunctionalInterface
public interface OrderEventPublisher {

  /**
   * Publish a placed-order event.
   *
   * @param event the event
   */
  void publish(OrderPlacedEvent event);
}
