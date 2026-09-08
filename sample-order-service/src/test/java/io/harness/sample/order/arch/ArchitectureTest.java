package io.harness.sample.order.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import io.harness.arch.LayeringRules;
import io.harness.arch.WireContractRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The whole adoption cost of the rule packs, for one service. */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      WireContractRules.importService("io.harness.sample.order");

  @Test
  @DisplayName("wire contract holds")
  void wireContractHolds() {
    WireContractRules.checkAll(CLASSES, "io.harness.sample.order.contract..");
  }

  @Test
  @DisplayName("layering holds")
  void layeringHolds() {
    LayeringRules.checkAll(CLASSES, "io.harness.sample.order.(*)..");
  }
}
