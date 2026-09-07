package io.harness.arch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.harness.arch.fixture.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves each wire-contract rule fires on a violating fixture and stays quiet on a clean one.
 *
 * <p>The negative cases matter more than the positive ones. A rule that never fires is
 * indistinguishable from a rule that is broken, and the second kind is worse than no rule because
 * it buys false confidence.
 */
class WireContractRulesTest {

  private static final JavaClasses FIXTURES =
      new ClassFileImporter().importPackages("io.harness.arch.fixture");

  private static JavaClasses only(Class<?>... classes) {
    return new ClassFileImporter().importClasses(classes);
  }

  @Test
  @DisplayName("a controller returning an entity directly is a violation")
  void directEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.DirectEntityController.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("an entity wrapped one generic level deep is still a violation")
  void wrappedEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(Fixtures.WrappedEntityController.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("an entity wrapped two generic levels deep is still a violation")
  void deeplyWrappedEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(
                            Fixtures.DeeplyWrappedEntityController.class,
                            Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("a controller returning a record passes")
  void recordReturnIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.CleanController.class, Fixtures.OrderResponse.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a public nested class inside a controller is a violation")
  void nestedTypeInControllerIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noPublicNestedClassesInControllers()
                    .check(
                        only(
                            Fixtures.ControllerWithNestedType.class,
                            Fixtures.ControllerWithNestedType.InlinePayload.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("InlinePayload");
  }

  @Test
  @DisplayName("a controller with no nested types passes")
  void controllerWithoutNestedTypesIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.noPublicNestedClassesInControllers()
                    .check(only(Fixtures.CleanController.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a wire-named type outside the contract package is a violation")
  void wireTypeOutsideContractPackageIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage("io.harness.contract..")
                    .check(only(Fixtures.OrderResponse.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderResponse");
  }

  @Test
  @DisplayName("a wire-named type inside the contract package passes")
  void wireTypeInsideContractPackageIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage("io.harness.arch.fixture..")
                    .check(only(Fixtures.OrderResponse.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("framework classes are exempt from the contract-package rule")
  void frameworkClassesAreExempt() {
    assertThatCode(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage("io.harness.contract..")
                    .check(only(org.springframework.http.HttpEntity.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an event publisher accepting an entity is a violation")
  void entityAsEventParameterIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInEventPublisherParameters("..fixture.events..")
                    .check(FIXTURES))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("the type-tree walk returns null for a clean type")
  void typeTreeWalkReturnsNullWhenClean() {
    JavaClasses clean = only(Fixtures.CleanController.class, Fixtures.OrderResponse.class);
    assertThat(
            WireContractRules.findEntityInTypeTree(
                clean.get(Fixtures.CleanController.class).getMethod("get").getReturnType()))
        .isNull();
  }

  @Test
  @DisplayName("a generic return type with clean arguments is not a false positive")
  void genericCleanReturnIsAccepted() {
    JavaClasses clean = only(Fixtures.GenericCleanController.class, Fixtures.OrderResponse.class);

    assertThatCode(() -> WireContractRules.noEntityInControllerReturnType().check(clean))
        .doesNotThrowAnyException();
    assertThat(
            WireContractRules.findEntityInTypeTree(
                clean.get(Fixtures.GenericCleanController.class).getMethod("get").getReturnType()))
        .isNull();
  }

  @Test
  @DisplayName("importService excludes test classes")
  void importServiceExcludesTests() {
    JavaClasses production = WireContractRules.importService("io.harness.arch");
    assertThat(production).isNotEmpty();
    assertThat(production.stream().map(c -> c.getName()))
        .noneMatch(name -> name.contains(".fixture."));
  }

  @Test
  @DisplayName("checkAll runs every rule and fails on the fixture package")
  void checkAllFailsOnViolatingFixtures() {
    assertThatThrownBy(() -> WireContractRules.checkAll(FIXTURES, "io.harness.contract.."))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("checkAll passes on a clean class set")
  void checkAllPassesOnCleanClasses() {
    assertThatCode(
            () ->
                WireContractRules.checkAll(
                    only(Fixtures.CleanController.class), "io.harness.arch.fixture.."))
        .doesNotThrowAnyException();
  }

  // ---------------------------------------------------------------------
  // Delegation tests.
  //
  // Each case below violates exactly one rule, so checkAll can only pass it
  // by actually invoking that rule. Mutation testing is what demanded these:
  // with only the aggregate tests above, deleting any single check() call
  // from checkAll left the suite green, which means a service adopting the
  // pack could silently lose a rule. See README, "What mutation testing found".
  // ---------------------------------------------------------------------

  @Test
  @DisplayName("checkAll invokes the nested-class rule")
  void checkAllInvokesNestedClassRule() {
    JavaClasses onlyNestedViolation =
        only(
            Fixtures.ControllerWithNestedType.class,
            Fixtures.ControllerWithNestedType.InlinePayload.class);

    assertThatThrownBy(
            () -> WireContractRules.checkAll(onlyNestedViolation, "io.harness.arch.fixture.."))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("InlinePayload");
  }

  @Test
  @DisplayName("checkAll invokes the contract-package rule")
  void checkAllInvokesContractPackageRule() {
    assertThatThrownBy(
            () -> WireContractRules.checkAll(only(Fixtures.OrderResponse.class), "io.nowhere.."))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderResponse");
  }

  @Test
  @DisplayName("checkAll invokes the controller-return rule")
  void checkAllInvokesControllerReturnRule() {
    JavaClasses onlyReturnViolation =
        only(Fixtures.DirectEntityController.class, Fixtures.OrderEntity.class);

    assertThatThrownBy(
            () -> WireContractRules.checkAll(onlyReturnViolation, "io.harness.arch.fixture.."))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("returns entity");
  }

  @Test
  @DisplayName("checkAll invokes the event-parameter rule")
  void checkAllInvokesEventParameterRule() {
    JavaClasses onlyEventViolation =
        only(
            io.harness.arch.fixture.events.OrderEventPublisher.class,
            Fixtures.OrderEntity.class,
            Fixtures.OrderResponse.class);

    assertThatThrownBy(
            () -> WireContractRules.checkAll(onlyEventViolation, "io.harness.arch.fixture.."))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("accepts entity");
  }
}
