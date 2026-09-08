package io.harness.sample.order.contract;

/** The wire contract for the order service. Money is in minor units. */
public interface OrderContracts {

  record CreateOrderRequest(String sku, int quantity, long unitPriceCents) {}

  record OrderResponse(String id, String sku, long totalCents) {}

  record OrderPlacedEvent(String orderId, long totalCents) {}
}
