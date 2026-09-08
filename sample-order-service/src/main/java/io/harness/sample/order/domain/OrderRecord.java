package io.harness.sample.order.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Persistence entity. Never crosses a boundary; the rules enforce that. */
@Entity
public class OrderRecord {

  @Id private String id;

  private String sku;

  private long totalCents;

  protected OrderRecord() {}

  public OrderRecord(String id, String sku, long totalCents) {
    this.id = id;
    this.sku = sku;
    this.totalCents = totalCents;
  }

  public String getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public long getTotalCents() {
    return totalCents;
  }
}
