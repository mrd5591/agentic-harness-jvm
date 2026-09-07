package io.harness.sample.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * The persistence entity. It exists here so the wire-contract rules have something real to forbid:
 * no controller may return this type, and no event publisher may accept it.
 *
 * <p>Money is held as a long of minor units. A double would be a rounding bug waiting for a
 * reconciliation to find it, and an agent will reach for double unless the type says otherwise.
 */
@Entity
public class OrderRecord {

  @Id private String id;

  private String sku;

  private long totalCents;

  /** Required by the persistence provider. */
  protected OrderRecord() {}

  /**
   * Create an order record.
   *
   * @param id the identifier
   * @param sku the item identifier
   * @param totalCents the total in minor units
   */
  public OrderRecord(String id, String sku, long totalCents) {
    this.id = id;
    this.sku = sku;
    this.totalCents = totalCents;
  }

  /**
   * The identifier.
   *
   * @return the id
   */
  public String getId() {
    return id;
  }

  /**
   * The item identifier.
   *
   * @return the sku
   */
  public String getSku() {
    return sku;
  }

  /**
   * The total in minor units.
   *
   * @return the total
   */
  public long getTotalCents() {
    return totalCents;
  }
}
