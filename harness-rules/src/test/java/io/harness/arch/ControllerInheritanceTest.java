package io.harness.arch;

import static io.harness.arch.fixture.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.arch.fixture.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The generic-CRUD-base hole, and the false-positive matrix that had to exist before it could be
 * closed.
 *
 * <p>The hole: rules scoped by {@code areDeclaredInClassesThat(controller())} key on the class that
 * <em>declares</em> a method. A method inherited from an un-annotated base is declared only in that
 * base, and javac emits no bridge method, so nothing in the controller declares it and the rule saw
 * nothing at all.
 *
 * <p>It needs two mechanisms, not one. The widening suggested when this was filed - "a class some
 * controller extends" - closes only the non-generic half. In the generic form the inherited method
 * returns the type variable {@code T}, whose erasure is {@code Object}: there is no entity anywhere
 * on the method to find, however the scope is widened. That half lives on the extends clause and
 * needs a class rule reading the supertype's type arguments.
 */
class ControllerInheritanceTest {

  @Nested
  @DisplayName("the hole: an entity reaches the wire through an un-annotated base")
  class TheHole {

    @Test
    @DisplayName("a generic base parameterised with an entity is a violation")
    void genericBaseBoundToAnEntityIsRejected() {
      assertThatThrownBy(
              () ->
                  WireContractRules.noEntityInControllerTypeArguments()
                      .check(
                          only(
                              Fixtures.BaseCrudController.class,
                              Fixtures.InheritingEntityController.class,
                              Fixtures.OrderEntity.class)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("OrderEntity")
          .hasMessageContaining("InheritingEntityController");
    }

    @Test
    @DisplayName("the entity is found however deep in the base's type arguments it sits")
    void deeplyNestedTypeArgumentIsRejected() {
      assertThatThrownBy(
              () ->
                  WireContractRules.noEntityInControllerTypeArguments()
                      .check(
                          only(
                              Fixtures.BaseCrudController.class,
                              Fixtures.DeepGenericInheritingController.class,
                              Fixtures.OrderEntity.class)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("OrderEntity");
    }

    @Test
    @DisplayName("a method inherited from a non-generic base is a violation")
    void inheritedEntityReturningMethodIsRejected() {
      assertThatThrownBy(
              () ->
                  WireContractRules.noEntityInControllerReturnType()
                      .check(
                          only(
                              Fixtures.BaseEntityReturningController.class,
                              Fixtures.InheritsEntityMethodController.class,
                              Fixtures.OrderEntity.class)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("OrderEntity");
    }
  }

  @Nested
  @DisplayName(
      "the false-positive matrix: a rule that fires on healthy input is worse than the gap")
  class FalsePositiveMatrix {

    @Test
    @DisplayName("a generic base parameterised with a contract record is clean")
    void genericBaseBoundToARecordIsAccepted() {
      assertThatCode(
              () ->
                  WireContractRules.noEntityInControllerTypeArguments()
                      .check(
                          only(
                              Fixtures.BaseCrudController.class,
                              Fixtures.InheritingCleanController.class)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "an interface a controller implements does not drag its methods into scope wrongly")
    void interfaceImplementingControllerIsAccepted() {
      assertThatCode(
              () -> {
                WireContractRules.noEntityInControllerTypeArguments()
                    .check(
                        only(
                            Fixtures.Describable.class,
                            Fixtures.InterfaceImplementingController.class));
                WireContractRules.noEntityInControllerReturnType()
                    .check(
                        only(
                            Fixtures.Describable.class,
                            Fixtures.InterfaceImplementingController.class));
              })
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a base no controller extends stays out of scope, entity or not")
    void unrelatedBaseIsNotChecked() {
      assertThatCode(
              () ->
                  WireContractRules.noEntityInControllerReturnType()
                      .check(only(Fixtures.UnrelatedBase.class, Fixtures.OrderEntity.class)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a supertype outside the imported set cannot pull anything into scope")
    void supertypeOutsideTheImportIsNotChecked() {
      // Only the controller is imported; its base is not. The widening is bounded by the
      // import, which is what keeps a shared base in another module from being audited
      // here - and keeps Object, a supertype of everything, out of every rule.
      assertThatCode(
              () ->
                  WireContractRules.noEntityInControllerReturnType()
                      .check(only(Fixtures.InheritsEntityMethodController.class)))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an ordinary clean controller is still clean under the widened scope")
    void cleanControllerStaysClean() {
      assertThatCode(
              () -> {
                WireContractRules.noEntityInControllerReturnType()
                    .check(only(Fixtures.CleanController.class));
                WireContractRules.noEntityInControllerTypeArguments()
                    .check(only(Fixtures.CleanController.class));
              })
          .doesNotThrowAnyException();
    }
  }
}
