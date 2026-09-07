package io.harness.arch.fixture.layering.cyclea;

import io.harness.arch.fixture.layering.cycleb.CycleB;

/** Violation fixture, one half of a package cycle. */
public class CycleA {

  /**
   * Call across to the other slice.
   *
   * @return a marker
   */
  public String callB() {
    return CycleB.marker();
  }

  /**
   * A marker the other slice calls back into.
   *
   * @return a marker
   */
  public static String marker() {
    return "a";
  }
}
