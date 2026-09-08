package io.harness.sample.order.domain;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderPlacedEvent;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import io.harness.sample.order.events.OrderEventPublisher;
import java.util.UUID;

/** Places orders and announces them. */
public class OrderService {

  private final OrderEventPublisher publisher;

  public OrderService(OrderEventPublisher publisher) {
    this.publisher = publisher;
  }

  /**
   * Place an order and publish the event.
   *
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

  /** Project the entity to its wire shape. */
  public static OrderResponse project(OrderRecord record) {
    return new OrderResponse(record.getId(), record.getSku(), record.getTotalCents());
  }
}
