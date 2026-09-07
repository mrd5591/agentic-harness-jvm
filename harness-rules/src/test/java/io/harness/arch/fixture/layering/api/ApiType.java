package io.harness.arch.fixture.layering.api;

/** A delivery-layer type. Nothing in the domain layer may reference it. */
public class ApiType {

  /**
   * A value the domain fixture reads, so the dependency is real rather than an unused import.
   *
   * @return a constant
   */
  public String describe() {
    return "api";
  }
}
