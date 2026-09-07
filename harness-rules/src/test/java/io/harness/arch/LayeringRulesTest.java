package io.harness.arch;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.harness.arch.fixture.layering.api.ApiType;
import io.harness.arch.fixture.layering.cyclea.CycleA;
import io.harness.arch.fixture.layering.cycleb.CycleB;
import io.harness.arch.fixture.layering.domain.DomainDependsOnApi;
import io.harness.arch.fixture.layering.inject.FieldInjected;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Exercises the layering pack against this module's own classes, which are clean by construction.
 *
 * <p>The violating cases for layering are covered in the sample services, where real packages named
 * {@code domain} and {@code api} exist. Here the job is to prove the rules load, run, and accept a
 * conforming codebase.
 */
class LayeringRulesTest {

  /**
   * This module's production classes only. The violating fixtures live under {@code ..fixture..} in
   * test sources, so they must be excluded here or the clean-code assertions below would be
   * asserting against deliberately broken code.
   */
  private static final JavaClasses OWN_CLASSES = WireContractRules.importService("io.harness.arch");

  @Test
  @DisplayName("domain-to-api rule accepts a codebase with no such layers")
  void domainDoesNotDependOnApiAcceptsCleanCode() {
    assertThatCode(
            () ->
                LayeringRules.domainDoesNotDependOnApi("..domain..", "..api..").check(OWN_CLASSES))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("cycle rule accepts an acyclic module")
  void noCyclesAcceptsAcyclicCode() {
    assertThatCode(() -> LayeringRules.noCyclesBetweenSlices("io.harness.(*)..").check(OWN_CLASSES))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("field-injection rule accepts a module with no injected fields")
  void noFieldInjectionAcceptsConstructorInjection() {
    assertThatCode(() -> LayeringRules.noFieldInjection().check(OWN_CLASSES))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("checkAll runs the whole pack")
  void checkAllRunsEveryRule() {
    assertThatCode(() -> LayeringRules.checkAll(OWN_CLASSES, "io.harness.(*).."))
        .doesNotThrowAnyException();
  }

  // Each case below violates exactly one rule, so checkAll can only pass it by
  // invoking that rule. Added because mutation testing showed that deleting an
  // individual check() call from checkAll left the suite green.

  private static final String LAYERING_SLICES = "io.harness.arch.fixture.layering.(*)..";

  @Test
  @DisplayName("checkAll invokes the domain-to-api rule")
  void checkAllInvokesDomainRule() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(DomainDependsOnApi.class, ApiType.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_SLICES))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DomainDependsOnApi");
  }

  @Test
  @DisplayName("checkAll invokes the cycle rule")
  void checkAllInvokesCycleRule() {
    JavaClasses classes = new ClassFileImporter().importClasses(CycleA.class, CycleB.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_SLICES))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Cycle");
  }

  @Test
  @DisplayName("checkAll invokes the field-injection rule")
  void checkAllInvokesFieldInjectionRule() {
    JavaClasses classes = new ClassFileImporter().importClasses(FieldInjected.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_SLICES))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Autowired");
  }
}
