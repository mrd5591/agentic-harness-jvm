package io.harness.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Rules that hold a service's internal shape.
 *
 * <p>Why these exist: an agent asked to "make the test pass" will reach for whatever import makes
 * the compiler happy, and the cheapest import is usually the one that inverts a dependency. Left
 * alone for a few hundred commits, the layering that exists in the README stops existing in the
 * bytecode. These rules fail the build at the moment the shortcut is taken, which is the only
 * moment it is cheap to fix.
 *
 * <p>Layer names are parameters rather than constants so a service can adopt the pack without
 * renaming its packages first.
 */
public interface LayeringRules {

  /**
   * The domain layer must not depend on the delivery layer. A domain type that imports a controller
   * or a serialization annotation has stopped being the model and started being a view.
   *
   * @param domainPackage matcher for the domain layer, e.g. {@code "..domain.."}
   * @param apiPackage matcher for the delivery layer, e.g. {@code "..api.."}
   * @return the rule
   */
  static ArchRule domainDoesNotDependOnApi(String domainPackage, String apiPackage) {
    return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(domainPackage)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(apiPackage)
        .because(
            "the domain is the stable core; a dependency on the delivery layer inverts that and "
                + "makes the model unusable from a second entry point")
        .allowEmptyShould(true);
  }

  /**
   * No cycles between the slices of a package tree. Cycles are the failure mode that makes a
   * codebase impossible to reason about incrementally, and they accumulate silently.
   *
   * @param slicePattern slice matcher, e.g. {@code "io.example.(*).."}
   * @return the rule
   */
  static ArchRule noCyclesBetweenSlices(String slicePattern) {
    return SlicesRuleDefinition.slices().matching(slicePattern).should().beFreeOfCycles();
  }

  /**
   * Field injection is untestable without a container and hides how many collaborators a class has.
   * Constructor injection makes the count obvious at the point where it becomes a problem.
   *
   * @return the rule
   */
  static ArchRule noFieldInjection() {
    return com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields()
        .should()
        .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
        .because(
            "constructor injection makes collaborator count visible and the class testable "
                + "without a container")
        .allowEmptyShould(true);
  }

  /**
   * Run every layering rule with the conventional package names used by the sample services.
   *
   * @param classes imported service classes
   * @param slicePattern slice matcher for the cycle check
   */
  static void checkAll(JavaClasses classes, String slicePattern) {
    domainDoesNotDependOnApi("..domain..", "..api..").check(classes);
    noCyclesBetweenSlices(slicePattern).check(classes);
    noFieldInjection().check(classes);
  }
}
