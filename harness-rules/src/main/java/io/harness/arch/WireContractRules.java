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

/** Rules that keep wire contracts from drifting across services. */
public interface WireContractRules {

  String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";

  String CONTROLLER = "org.springframework.stereotype.Controller";

  String JPA_ENTITY = "jakarta.persistence.Entity";

  String WIRE_TYPE_NAME_PATTERN = ".*(Request|Response|Dto)";

  /** Import a service's production classes, excluding tests. */
  static JavaClasses importService(String... packages) {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages(packages);
  }

  /** Public nested classes in controllers shadow canonical contract types. */
  static ArchRule noPublicNestedClassesInControllers() {
    return ArchRuleDefinition.noClasses()
        .that()
        .arePublic()
        .and(nestedInsideController())
        .should()
        .haveNameMatching(".*")
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
        .areDeclaredInClassesThat()
        .areAnnotatedWith(REST_CONTROLLER)
        .should(notExposeEntityInReturnType())
        .because("controller return types are the wire contract")
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
        .should(notAcceptEntityAsParameter())
        .because("event payloads are serialized with no compile-time contract")
        .allowEmptyShould(true);
  }

  /** Run every wire-contract rule. */
  static void checkAll(JavaClasses classes, String canonicalContractPackage) {
    noPublicNestedClassesInControllers().check(classes);
    wireTypesLiveInCanonicalPackage(canonicalContractPackage).check(classes);
    noEntityInControllerReturnType().check(classes);
    noEntityInEventPublisherParameters("..events..").check(classes);
  }

  /** First persistence entity in a type and its generic arguments, or null. */
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

  // Positive condition on methods().should(...) rather than noMethods().should(...):
  // under the negative form ArchUnit inverts event polarity and violations pass silently.
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
