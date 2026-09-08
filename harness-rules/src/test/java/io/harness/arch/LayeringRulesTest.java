package io.harness.arch;

import static io.harness.arch.fixture.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import io.harness.arch.fixture.layering.api.ApiType;
import io.harness.arch.fixture.layering.cyclea.CycleA;
import io.harness.arch.fixture.layering.cycleb.CycleB;
import io.harness.arch.fixture.layering.domain.DomainDependsOnApi;
import io.harness.arch.fixture.layering.inject.FieldInjected;
import io.harness.arch.fixture.layering.inject.ValueInjected;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two halves: the pack is quiet on this module's own production classes, which are clean by
 * construction, and it fires on the violating fixtures under {@code fixture.layering}.
 */
class LayeringRulesTest {

  /**
   * This module's production classes only. The violating fixtures live under {@code ..fixture..} in
   * test sources, so they must be excluded here or the clean-code assertions below would be
   * asserting against deliberately broken code.
   */
  private static final JavaClasses OWN_CLASSES = WireContractRules.importService("io.harness.arch");

  private static final String LAYERING_BASE = "io.harness.arch.fixture.layering";

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
  @DisplayName("field-injection rule fires on an @Autowired field")
  void noFieldInjectionRejectsAutowiredField() {
    assertThatThrownBy(() -> LayeringRules.noFieldInjection().check(only(FieldInjected.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Autowired");
  }

  @Test
  @DisplayName("field-injection rule fires on a @Value field, not just @Autowired")
  void noFieldInjectionRejectsValueField() {
    assertThatThrownBy(() -> LayeringRules.noFieldInjection().check(only(ValueInjected.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Value");
  }

  @Test
  @DisplayName("checkAll runs the whole pack")
  void checkAllRunsEveryRule() {
    assertThatCode(() -> LayeringRules.checkAll(OWN_CLASSES, "io.harness"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("checkAll derives the domain and api packages from the base package")
  void checkAllInvokesDomainRule() {
    JavaClasses classes = only(DomainDependsOnApi.class, ApiType.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("DomainDependsOnApi");
    // Under a base that owns none of these packages the domain rule has nothing to say, and the
    // slice rule then refuses to pass on an empty match, so the failure names the derived pattern.
    assertThatThrownBy(() -> LayeringRules.checkAll(classes, "io.nowhere"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("io.nowhere.(*)..")
        .hasMessageNotContaining("DomainDependsOnApi");
  }

  @Test
  @DisplayName("checkAll derives the slice pattern from the base package")
  void checkAllInvokesCycleRule() {
    JavaClasses classes = only(CycleA.class, CycleB.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Cycle");
    assertThatThrownBy(() -> LayeringRules.checkAll(classes, "io.nowhere"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("io.nowhere.(*)..")
        .hasMessageNotContaining("Cycle");
  }

  @Test
  @DisplayName("checkAll invokes the field-injection rule")
  void checkAllInvokesFieldInjectionRule() {
    JavaClasses classes = only(FieldInjected.class);

    assertThatThrownBy(() -> LayeringRules.checkAll(classes, LAYERING_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Autowired");
  }
}
