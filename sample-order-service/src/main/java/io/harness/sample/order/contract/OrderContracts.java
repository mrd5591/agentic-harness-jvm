package io.harness.sample.order.contract;

/**
 * The wire contract for the order service, in one file so the canonical shapes are impossible to
 * miss when an agent goes looking for them.
 *
 * <p>Everything a caller can see is declared here. The architecture rules enforce that: any type
 * named Request, Response or Dto must live in this package, and nothing else may declare one.
 */
public interface OrderContracts {

  /**
   * A request to place an order.
   *
   * @param sku the item identifier
   * @param quantity how many, must be positive
   * @param unitPriceCents price per unit in minor units, must be positive
   */
  record CreateOrderRequest(String sku, int quantity, long unitPriceCents) {}

  /**
   * The public projection of a placed order.
   *
   * @param id the order identifier
   * @param sku the item identifier
   * @param totalCents the computed total in minor units
   */
  record OrderResponse(String id, String sku, long totalCents) {}

  /**
   * The event published when an order is placed. Consumed by the ledger service.
   *
   * @param orderId the order identifier
   * @param totalCents the amount to post, in minor units
   */
  record OrderPlacedEvent(String orderId, long totalCents) {}
}
