package io.harness.arch.fixture.layering.cycleb;

import io.harness.arch.fixture.layering.cyclea.CycleA;

/** Violation fixture, the other half of the package cycle. */
public class CycleB {

  /**
   * Call back into the first slice, closing the cycle.
   *
   * @return a marker
   */
  public String callA() {
    return CycleA.marker();
  }

  /**
   * A marker the first slice calls into.
   *
   * @return a marker
   */
  public static String marker() {
    return "b";
  }
}
