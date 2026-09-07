package io.harness.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Rules that keep the wire contract from drifting.
 *
 * <p>Why these exist: in a multi-service codebase built with coding agents, the single most
 * expensive recurring failure is not a wrong algorithm. It is the same concept acquiring three
 * different shapes in three services, because an agent that cannot find the canonical type will
 * cheerfully declare a local one. Reviewers miss it, the merged API spec grows duplicate schemas,
 * and the frontend papers over the difference with null-coalescing chains. These rules make that
 * class of drift unmergeable.
 *
 * <p>This is an interface rather than a final class with a private constructor. A private
 * constructor is an uncovered line under a 100% line-coverage gate, and the usual workaround
 * (reflectively invoking it in a test) is a test that asserts nothing. An interface has no
 * constructor to cover. See README, "What the ratchet forced".
 *
 * <p>Adopt in a service with one test:
 *
 * <pre>{@code
 * class WireContractTest {
 *   @Test
 *   void enforceWireContract() {
 *     WireContractRules.checkAll(
 *         WireContractRules.importService("com.example.orders"), "com.example.contract");
 *   }
 * }
 * }</pre>
 */
public interface WireContractRules {

  /**
   * Fully-qualified annotation names, referenced as strings so this module stays dependency-light.
   */
  String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";

  /** Spring's non-REST controller stereotype. */
  String CONTROLLER = "org.springframework.stereotype.Controller";

  /** JPA entity annotation. */
  String JPA_ENTITY = "jakarta.persistence.Entity";

  /** Suffixes that mark a type as part of the wire contract. */
  String WIRE_TYPE_NAME_PATTERN = ".*(Request|Response|Dto)";

  /**
   * Import a service's production classes plus the canonical contract package, excluding tests.
   *
   * @param packages packages to import; the first is conventionally the service root
   * @return the imported classes
   */
  static JavaClasses importService(String... packages) {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(packages);
  }

  /**
   * Public nested classes inside controllers shadow canonical contract types and produce duplicate
   * schemas in a merged OpenAPI document.
   *
   * @return the rule
   */
  static ArchRule noPublicNestedClassesInControllers() {
    return ArchRuleDefinition.noClasses()
        .that()
        .arePublic()
        .and(nestedInsideController())
        .should()
        .haveNameMatching(".*")
        .because(
            "a public nested class inside a controller shadows the canonical contract type and "
                + "creates a duplicate schema in the merged API spec; move it to the contract "
                + "package or use the type that already exists")
        .allowEmptyShould(true);
  }

  /**
   * Any type named like a wire contract must live in the one package that defines wire contracts.
   *
   * @param canonicalContractPackage package matcher, e.g. {@code "com.example.contract.."}
   * @return the rule
   */
  static ArchRule wireTypesLiveInCanonicalPackage(String canonicalContractPackage) {
    return ArchRuleDefinition.classes()
        .that()
        .haveNameMatching(WIRE_TYPE_NAME_PATTERN)
        .and(outsideFrameworkPackages())
        .should()
        .resideInAPackage(canonicalContractPackage)
        .because(
            "a class named Request/Response/Dto is a wire contract and must live in "
                + canonicalContractPackage
                + "; a service-local copy silently conflicts with the canonical schema")
        .allowEmptyShould(true);
  }

  /**
   * Controller methods must not return persistence entities, directly or wrapped in any depth of
   * generics. Entities carry lazy relationships that fail outside a session and internal field
   * names that drift from the documented shape.
   *
   * @return the rule
   */
  static ArchRule noEntityInControllerReturnType() {
    return ArchRuleDefinition.methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat()
        .areAnnotatedWith(REST_CONTROLLER)
        .should(notExposeEntityInReturnType())
        .because(
            "controller return types are the wire contract; project the entity to a record in the "
                + "contract package before returning it")
        .allowEmptyShould(true);
  }

  /**
   * Event publishers must not accept persistence entities. Event payloads are serialized without a
   * compile-time contract, so the method signature is the only place the invariant can be enforced.
   *
   * @param publisherPackage package matcher for publishers, e.g. {@code "..events.."}
   * @return the rule
   */
  static ArchRule noEntityInEventPublisherParameters(String publisherPackage) {
    return ArchRuleDefinition.methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat()
        .resideInAPackage(publisherPackage)
        .should(notAcceptEntityAsParameter())
        .because(
            "event payloads are serialized with no compile-time contract; project to a record at "
                + "the caller so the published shape is reviewable")
        .allowEmptyShould(true);
  }

  /**
   * Run every wire-contract rule. This is the method services call.
   *
   * @param classes imported service classes
   * @param canonicalContractPackage package matcher for the contract package
   */
  static void checkAll(JavaClasses classes, String canonicalContractPackage) {
    noPublicNestedClassesInControllers().check(classes);
    wireTypesLiveInCanonicalPackage(canonicalContractPackage).check(classes);
    noEntityInControllerReturnType().check(classes);
    noEntityInEventPublisherParameters("..events..").check(classes);
  }

  /**
   * Walk a type and its generic arguments recursively, returning the first persistence entity
   * found. The recursion is the point: {@code ResponseEntity<Page<OrderEntity>>} leaks just as
   * surely as a bare {@code OrderEntity}, and only a tree walk catches both.
   *
   * @param type the type to inspect
   * @return the leaked entity, or null when the type tree is clean
   */
  static JavaClass findEntityInTypeTree(JavaType type) {
    JavaClass raw = type.toErasure();
    if (raw.isAnnotatedWith(JPA_ENTITY)) {
      return raw;
    }
    if (type instanceof JavaParameterizedType parameterized) {
      for (JavaType argument : parameterized.getActualTypeArguments()) {
        JavaClass nested = findEntityInTypeTree(argument);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }

  /**
   * Condition: the method's return type tree contains no persistence entity.
   *
   * <p>Written as a positive condition on {@code methods().should(...)} rather than a negative one
   * on {@code noMethods().should(...)}. Under the negative form ArchUnit inverts the event polarity
   * and a violation reported here would silently pass.
   *
   * @return the condition
   */
  static ArchCondition<JavaMethod> notExposeEntityInReturnType() {
    return new ArchCondition<>("not return a persistence entity, at any generic depth") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        JavaClass leaked = findEntityInTypeTree(method.getReturnType());
        if (leaked != null) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  String.format(
                      "%s.%s returns entity %s; project it to a record in the contract package",
                      method.getOwner().getSimpleName(),
                      method.getName(),
                      leaked.getSimpleName())));
        }
      }
    };
  }

  /**
   * Condition: no parameter's type tree contains a persistence entity.
   *
   * @return the condition
   */
  static ArchCondition<JavaMethod> notAcceptEntityAsParameter() {
    return new ArchCondition<>("not accept a persistence entity, at any generic depth") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (JavaType parameter : method.getParameterTypes()) {
          JavaClass leaked = findEntityInTypeTree(parameter);
          if (leaked != null) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "%s.%s accepts entity %s; project it to a record at the caller",
                        method.getOwner().getSimpleName(),
                        method.getName(),
                        leaked.getSimpleName())));
            return;
          }
        }
      }
    };
  }

  /**
   * Predicate: the class is declared inside a controller.
   *
   * @return the predicate
   */
  static DescribedPredicate<JavaClass> nestedInsideController() {
    return new DescribedPredicate<>("nested inside a controller class") {
      @Override
      public boolean test(JavaClass javaClass) {
        return javaClass
            .getEnclosingClass()
            .map(
                enclosing ->
                    enclosing.isAnnotatedWith(REST_CONTROLLER)
                        || enclosing.isAnnotatedWith(CONTROLLER))
            .orElse(false);
      }
    };
  }

  /**
   * Predicate: the class is not framework or JDK code, which must not be rewritten.
   *
   * @return the predicate
   */
  static DescribedPredicate<JavaClass> outsideFrameworkPackages() {
    return DescribedPredicate.not(
        JavaClass.Predicates.resideInAnyPackage(
            "java..",
            "javax..",
            "jakarta..",
            "org.springframework..",
            "org.springdoc..",
            "io.swagger..",
            "com.fasterxml.."));
  }
}
