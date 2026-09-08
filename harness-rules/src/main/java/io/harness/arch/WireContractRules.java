package io.harness.arch;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Rules that keep wire contracts from drifting across services. */
public interface WireContractRules {

  String CONTROLLER = "org.springframework.stereotype.Controller";

  String JPA_ENTITY = "jakarta.persistence.Entity";

  String WIRE_TYPE_NAME_PATTERN = ".*(Request|Response|Dto)";

  /**
   * Import a service's production classes, excluding tests.
   *
   * <p>Guaranteed non-empty. Every rule in this pack allows an empty class set, so an import that
   * matched nothing would turn a service's whole gate green while checking nothing, permanently and
   * invisibly. A service importing its own packages and finding no class has a typo in the package
   * name, never a legitimate empty state, so this refuses to hand back the empty result.
   *
   * @param packages the service's base packages
   * @return the imported production classes, never empty
   * @throws IllegalArgumentException if no production class resides in any of {@code packages}
   */
  static JavaClasses importService(String... packages) {
    JavaClasses imported =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(packages);
    if (imported.isEmpty()) {
      throw new IllegalArgumentException(
          "no production classes found in "
              + List.of(packages)
              + "; check the package name for a typo, because every rule passes vacuously on an "
              + "empty class set");
    }
    return imported;
  }

  /**
   * What counts as a controller, defined once for every rule that needs it. Spring's
   * {@code @RestController} is meta-annotated with {@code @Controller}, and so is any project
   * stereotype built on either, so meta-annotation is the check that cannot be routed around.
   */
  static DescribedPredicate<JavaClass> controller() {
    return CanBeAnnotated.Predicates.annotatedWith(CONTROLLER)
        .or(CanBeAnnotated.Predicates.metaAnnotatedWith(CONTROLLER))
        .as("a controller (annotated or meta-annotated with @Controller)")
        .forSubtype();
  }

  /** Public nested classes in controllers shadow canonical contract types. */
  static ArchRule noPublicNestedClassesInControllers() {
    return ArchRuleDefinition.noClasses()
        .that()
        .areNestedClasses()
        .and(enclosedByController())
        .should()
        .bePublic()
        .because(
            "a public nested class inside a controller shadows the canonical contract type and "
                + "creates a duplicate schema in the merged API spec")
        .allowEmptyShould(true);
  }

  /** Types named like wire contracts must live in the contract package. */
  static ArchRule wireTypesLiveInCanonicalPackage(String canonicalContractPackage) {
    return ArchRuleDefinition.classes()
        .that()
        .haveNameMatching(WIRE_TYPE_NAME_PATTERN)
        .and(outsideFrameworkPackages())
        .should()
        .resideInAPackage(canonicalContractPackage)
        .because(
            "a class named Request/Response/Dto is a wire contract and must live in "
                + canonicalContractPackage)
        .allowEmptyShould(true);
  }

  /** Controllers must not return persistence entities at any generic depth. */
  static ArchRule noEntityInControllerReturnType() {
    return ArchRuleDefinition.methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat(controller())
        .should(
            entityFree(
                "return",
                method -> List.of(method.getReturnType()),
                "project it to a record in the contract package"))
        .because("controller return types are the wire contract")
        .allowEmptyShould(true);
  }

  /**
   * Controllers must not accept persistence entities at any generic depth. Inbound is the same
   * boundary as outbound and the more dangerous direction: an entity bound from a request body
   * exposes every settable field, including the ones the caller was never meant to set.
   */
  static ArchRule noEntityInControllerParameters() {
    return ArchRuleDefinition.methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat(controller())
        .should(
            entityFree(
                "accept",
                JavaMethod::getParameterTypes,
                "bind a record from the contract package instead"))
        .because("a request body binds untrusted input to every settable field of the type")
        .allowEmptyShould(true);
  }

  /** Event publishers must not accept persistence entities at any generic depth. */
  static ArchRule noEntityInEventPublisherParameters(String publisherPackage) {
    return ArchRuleDefinition.methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat()
        .resideInAPackage(publisherPackage)
        .should(
            entityFree(
                "accept", JavaMethod::getParameterTypes, "project it to a record at the caller"))
        .because("event payloads are serialized with no compile-time contract")
        .allowEmptyShould(true);
  }

  /**
   * Run every wire-contract rule against a service laid out under one base package: contracts in
   * {@code <base>.contract}, publishers in {@code <base>.events}. Deriving both from the base means
   * a service cannot configure one and silently leave the other matching nothing.
   */
  static void checkAll(JavaClasses classes, String basePackage) {
    noPublicNestedClassesInControllers().check(classes);
    wireTypesLiveInCanonicalPackage(basePackage + ".contract..").check(classes);
    noEntityInControllerReturnType().check(classes);
    noEntityInControllerParameters().check(classes);
    noEntityInEventPublisherParameters(basePackage + ".events..").check(classes);
  }

  /**
   * First persistence entity in a type tree, or null. Descends through generic arguments, wildcard
   * bounds in both directions, type-variable bounds, and array components.
   */
  static JavaClass findEntityInTypeTree(JavaType type) {
    return findEntity(type, new HashSet<>());
  }

  private static JavaClass findEntity(JavaType type, Set<String> seen) {
    // Recursive bounds such as <T extends Comparable<T>> would otherwise never terminate.
    if (!seen.add(type.getName())) {
      return null;
    }
    JavaClass raw = type.toErasure();
    if (raw.isAnnotatedWith(JPA_ENTITY)) {
      return raw;
    }
    for (JavaType child : childrenOf(type, raw)) {
      JavaClass nested = findEntity(child, seen);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  private static List<JavaType> childrenOf(JavaType type, JavaClass raw) {
    if (type instanceof JavaParameterizedType parameterized) {
      return parameterized.getActualTypeArguments();
    }
    if (type instanceof JavaWildcardType wildcard) {
      List<JavaType> bounds = new ArrayList<>(wildcard.getUpperBounds());
      bounds.addAll(wildcard.getLowerBounds());
      return bounds;
    }
    if (type instanceof JavaTypeVariable<?> variable) {
      return variable.getUpperBounds();
    }
    if (type instanceof JavaGenericArrayType array) {
      return List.of(array.getComponentType());
    }
    if (raw.isArray()) {
      return List.of(raw.getBaseComponentType());
    }
    return List.of();
  }

  // Positive condition on methods().should(...) rather than noMethods().should(...):
  // under the negative form ArchUnit inverts event polarity and violations pass silently.
  static ArchCondition<JavaMethod> entityFree(
      String verb, Function<JavaMethod, List<JavaType>> typesOf, String advice) {
    return new ArchCondition<>("not " + verb + " a persistence entity, at any generic depth") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        for (JavaType type : typesOf.apply(method)) {
          JavaClass leaked = findEntityInTypeTree(type);
          if (leaked != null) {
            events.add(
                SimpleConditionEvent.violated(
                    method,
                    String.format(
                        "%s.%s %ss entity %s; %s",
                        method.getOwner().getSimpleName(),
                        method.getName(),
                        verb,
                        leaked.getSimpleName(),
                        advice)));
            return;
          }
        }
      }
    };
  }

  /** True when any enclosing class, at any depth, is a controller. */
  static DescribedPredicate<JavaClass> enclosedByController() {
    DescribedPredicate<JavaClass> controller = controller();
    return new DescribedPredicate<>("enclosed by a controller class") {
      @Override
      public boolean test(JavaClass javaClass) {
        Optional<JavaClass> enclosing = javaClass.getEnclosingClass();
        while (enclosing.isPresent()) {
          if (controller.test(enclosing.get())) {
            return true;
          }
          enclosing = enclosing.get().getEnclosingClass();
        }
        return false;
      }
    };
  }

  static DescribedPredicate<JavaClass> outsideFrameworkPackages() {
    return JavaClass.Predicates.resideOutsideOfPackages(
        "java..",
        "javax..",
        "jakarta..",
        "org.springframework..",
        "org.springdoc..",
        "io.swagger..",
        "com.fasterxml..");
  }
}
