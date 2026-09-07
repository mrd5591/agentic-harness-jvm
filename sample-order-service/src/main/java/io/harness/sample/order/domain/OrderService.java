package io.harness.sample.order.domain;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import io.harness.sample.order.events.OrderEventPublisher;
import java.util.UUID;

/**
 * Places orders and announces them.
 *
 * <p>Collaborators arrive through the constructor. That is a rule the build enforces rather than a
 * preference: field injection hides how many things a class touches, and the count is the earliest
 * signal that a class has taken on a second job.
 */
public class OrderService {

  private final OrderEventPublisher publisher;

  /**
   * Create the service.
   *
   * @param publisher where placed-order events go
   */
  public OrderService(OrderEventPublisher publisher) {
    this.publisher = publisher;
  }

  /**
   * Place an order, publish the event, and return the public projection.
   *
   * @param request the order to place
   * @return the projection of what was placed
   * @throws IllegalArgumentException when quantity or price is not positive
   */
  public OrderResponse place(CreateOrderRequest request) {
    if (request.quantity() <= 0) {
      throw new IllegalArgumentException("quantity must be positive");
    }
    if (request.unitPriceCents() <= 0) {
      throw new IllegalArgumentException("unitPriceCents must be positive");
    }
    long total = Math.multiplyExact((long) request.quantity(), request.unitPriceCents());
    OrderRecord record = new OrderRecord(UUID.randomUUID().toString(), request.sku(), total);
    publisher.publish(new OrderPlacedEvent(record.getId(), record.getTotalCents()));
    return project(record);
  }

  /**
   * Project the entity to its wire shape. Every entity-to-contract conversion goes through a named
   * method like this one, so there is a single place to look when a field drifts.
   *
   * @param record the entity
   * @return the projection
   */
  public static OrderResponse project(OrderRecord record) {
    return new OrderResponse(record.getId(), record.getSku(), record.getTotalCents());
  }
}
