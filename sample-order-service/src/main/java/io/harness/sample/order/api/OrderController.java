package io.harness.sample.order.api;

import io.harness.sample.order.contract.OrderContracts.CreateOrderRequest;
import io.harness.sample.order.contract.OrderContracts.OrderResponse;
import io.harness.sample.order.domain.OrderService;
import org.springframework.web.bind.annotation.RestController;

/** Delivery layer. Returns contract records only. */
@RestController
public class OrderController {

  private final OrderService orders;

  public OrderController(OrderService orders) {
    this.orders = orders;
  }

  public OrderResponse place(CreateOrderRequest request) {
    return orders.place(request);
  }
}
