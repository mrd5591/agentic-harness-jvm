package io.harness.arch.fixture.layering.domain;

import io.harness.arch.fixture.layering.api.ApiType;

/** Violation fixture: a domain type reaching into the delivery layer. */
public class DomainDependsOnApi {

  /**
   * Reach upward, which is the inversion the rule exists to catch.
   *
   * @return whatever the delivery layer says
   */
  public String leak() {
    return new ApiType().describe();
  }
}
