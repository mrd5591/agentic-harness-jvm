package io.harness.sample.order.api;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import io.harness.sample.order.domain.OrderService;
import org.springframework.web.bind.annotation.RestController;

/**
 * The delivery layer. It returns contract records and nothing else.
 *
 * <p>There are no nested types here, which is also enforced: an inline {@code public static class}
 * inside a controller is the fastest way for an agent to invent a second, drifted shape for a
 * concept that already has one.
 */
@RestController
public class OrderController {

  private final OrderService orders;

  /**
   * Create the controller.
   *
   * @param orders the domain service
   */
  public OrderController(OrderService orders) {
    this.orders = orders;
  }

  /**
   * Place an order.
   *
   * @param request the order to place
   * @return the placed order's projection
   */
  public OrderResponse place(CreateOrderRequest request) {
    return orders.place(request);
  }
}
