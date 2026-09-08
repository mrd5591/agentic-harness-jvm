package io.harness.arch;

import static io.harness.arch.fixture.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.harness.arch.fixture.Fixtures;
import io.harness.arch.fixture.contract.OrderResponse;
import io.harness.arch.fixture.events.CleanEventPublisher;
import io.harness.arch.fixture.events.OrderEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Proves each wire-contract rule fires on a violating fixture and stays quiet on a clean one. */
class WireContractRulesTest {

  private static final String FIXTURE_BASE = "io.harness.arch.fixture";

  private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_BASE);

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
  @DisplayName("an entity array is a violation")
  void arrayEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.ArrayEntityController.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("an array of generic types carrying an entity is a violation")
  void genericArrayEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(
                            Fixtures.GenericArrayEntityController.class,
                            Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("an entity behind a wildcard upper bound is a violation")
  void wildcardBoundEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(Fixtures.WildcardEntityController.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("an entity behind a type-variable bound is a violation")
  void typeVariableBoundEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(
                            Fixtures.TypeVariableEntityController.class,
                            Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderEntity");
  }

  @Test
  @DisplayName("a recursive type-variable bound terminates and is clean")
  void recursiveBoundIsAccepted() {
    JavaClasses clean = only(Fixtures.RecursiveBoundController.class);

    assertThatCode(() -> WireContractRules.noEntityInControllerReturnType().check(clean))
        .doesNotThrowAnyException();
    assertThat(
            WireContractRules.findEntityInTypeTree(
                clean
                    .get(Fixtures.RecursiveBoundController.class)
                    .getMethod("get")
                    .getReturnType()))
        .isNull();
  }

  @Test
  @DisplayName("a controller returning a record passes")
  void recordReturnIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.CleanController.class, OrderResponse.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a plain @Controller returning an entity is a violation")
  void plainControllerEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.PlainController.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PlainController.get returns entity OrderEntity");
  }

  @Test
  @DisplayName("a project stereotype meta-annotated with @RestController is a controller")
  void stereotypedControllerEntityReturnIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(
                            Fixtures.StereotypedController.class,
                            Fixtures.ApiEndpoint.class,
                            Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("StereotypedController.get returns entity OrderEntity");
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
  @DisplayName("a public nested class inside a plain @Controller is a violation")
  void nestedTypeInPlainControllerIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noPublicNestedClassesInControllers()
                    .check(
                        only(
                            Fixtures.PlainController.class,
                            Fixtures.PlainController.InlinePayload.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("InlinePayload");
  }

  @Test
  @DisplayName("a public class nested two levels inside a controller is a violation")
  void deeplyNestedTypeInControllerIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noPublicNestedClassesInControllers()
                    .check(only(Fixtures.deepControllerTree())))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Inner")
        .hasMessageNotContaining("Holder>");
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
  @DisplayName("a nested class outside any controller passes")
  void nestedTypeOutsideControllerIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.noPublicNestedClassesInControllers()
                    .check(only(Fixtures.class, Fixtures.OrderEntity.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("a wire-named type outside the contract package is a violation")
  void wireTypeOutsideContractPackageIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage("io.harness.contract..")
                    .check(only(OrderResponse.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderResponse");
  }

  @Test
  @DisplayName("a wire-named type inside the contract package passes")
  void wireTypeInsideContractPackageIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage(FIXTURE_BASE + ".contract..")
                    .check(only(OrderResponse.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("framework classes are exempt from the contract-package rule")
  void frameworkClassesAreExempt() {
    // WebRequest matches the wire-type name pattern, so only the package exemption saves it.
    assertThatCode(
            () ->
                WireContractRules.wireTypesLiveInCanonicalPackage("io.harness.contract..")
                    .check(only(org.springframework.web.context.request.WebRequest.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an event publisher accepting an entity is a violation")
  void entityAsEventParameterIsRejected() {
    assertThatThrownBy(
            () ->
                WireContractRules.noEntityInEventPublisherParameters("..fixture.events..")
                    .check(only(OrderEventPublisher.class, Fixtures.OrderEntity.class)))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("publishLeaky accepts entity OrderEntity")
        .hasMessageContaining("publishThroughLowerBound accepts entity OrderEntity");
  }

  @Test
  @DisplayName("an event publisher accepting the contract record passes")
  void recordAsEventParameterIsAccepted() {
    assertThatCode(
            () ->
                WireContractRules.noEntityInEventPublisherParameters("..fixture.events..")
                    .check(only(CleanEventPublisher.class, OrderResponse.class)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("the type-tree walk returns null for a clean type")
  void typeTreeWalkReturnsNullWhenClean() {
    JavaClasses clean = only(Fixtures.CleanController.class, OrderResponse.class);
    assertThat(
            WireContractRules.findEntityInTypeTree(
                clean.get(Fixtures.CleanController.class).getMethod("get").getReturnType()))
        .isNull();
  }

  @Test
  @DisplayName("a generic return type with clean arguments is not a false positive")
  void genericCleanReturnIsAccepted() {
    JavaClasses clean = only(Fixtures.GenericCleanController.class, OrderResponse.class);

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
    assertThatThrownBy(() -> WireContractRules.checkAll(FIXTURES, FIXTURE_BASE))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  @DisplayName("checkAll passes on a clean class set")
  void checkAllPassesOnCleanClasses() {
    assertThatCode(
            () ->
                WireContractRules.checkAll(
                    only(
                        Fixtures.CleanController.class,
                        OrderResponse.class,
                        CleanEventPublisher.class),
                    FIXTURE_BASE))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("checkAll invokes the nested-class rule")
  void checkAllInvokesNestedClassRule() {
    JavaClasses onlyNestedViolation =
        only(
            Fixtures.ControllerWithNestedType.class,
            Fixtures.ControllerWithNestedType.InlinePayload.class);

    assertThatThrownBy(() -> WireContractRules.checkAll(onlyNestedViolation, FIXTURE_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("InlinePayload");
  }

  @Test
  @DisplayName("checkAll derives the contract package from the base package")
  void checkAllInvokesContractPackageRule() {
    assertThatThrownBy(() -> WireContractRules.checkAll(only(OrderResponse.class), "io.nowhere"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("OrderResponse")
        .hasMessageContaining("io.nowhere.contract..");
  }

  @Test
  @DisplayName("checkAll invokes the controller-return rule")
  void checkAllInvokesControllerReturnRule() {
    JavaClasses onlyReturnViolation =
        only(Fixtures.DirectEntityController.class, Fixtures.OrderEntity.class);

    assertThatThrownBy(() -> WireContractRules.checkAll(onlyReturnViolation, FIXTURE_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("returns entity");
  }

  @Test
  @DisplayName("checkAll derives the publisher package from the base package")
  void checkAllInvokesEventParameterRule() {
    JavaClasses onlyEventViolation = only(OrderEventPublisher.class, Fixtures.OrderEntity.class);

    assertThatThrownBy(() -> WireContractRules.checkAll(onlyEventViolation, FIXTURE_BASE))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("accepts entity");
    // The same classes under a different base are not publishers, so the rule matches nothing.
    assertThatCode(() -> WireContractRules.checkAll(onlyEventViolation, "io.nowhere"))
        .doesNotThrowAnyException();
  }
}
