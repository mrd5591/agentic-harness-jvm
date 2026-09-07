package io.harness.sample.ledger.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import io.harness.arch.LayeringRules;
import io.harness.arch.WireContractRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The same two tests as the order service, with one string changed. That is the whole point. */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      WireContractRules.importService("io.harness.sample.ledger");

  @Test
  @DisplayName("wire contract holds")
  void wireContractHolds() {
    WireContractRules.checkAll(CLASSES, "io.harness.sample.ledger.contract..");
  }

  @Test
  @DisplayName("layering holds")
  void layeringHolds() {
    LayeringRules.checkAll(CLASSES, "io.harness.sample.ledger.(*)..");
  }
}
