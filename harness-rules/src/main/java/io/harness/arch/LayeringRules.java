package io.harness.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/** Rules that hold a service's internal shape. */
public interface LayeringRules {

  /** The domain layer must not depend on the delivery layer. */
  static ArchRule domainDoesNotDependOnApi(String domainPackage, String apiPackage) {
    return ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage(domainPackage)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(apiPackage)
        .because("a dependency on the delivery layer inverts the domain's role as the stable core")
        .allowEmptyShould(true);
  }

  /** No cycles between the slices of a package tree. */
  static ArchRule noCyclesBetweenSlices(String slicePattern) {
    return SlicesRuleDefinition.slices().matching(slicePattern).should().beFreeOfCycles();
  }

  /** Constructor injection only. */
  static ArchRule noFieldInjection() {
    return ArchRuleDefinition.noFields()
        .should()
        .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
        .because("constructor injection keeps collaborator count visible and the class testable")
        .allowEmptyShould(true);
  }

  /** Run every layering rule. */
  static void checkAll(JavaClasses classes, String slicePattern) {
    domainDoesNotDependOnApi("..domain..", "..api..").check(classes);
    noCyclesBetweenSlices(slicePattern).check(classes);
    noFieldInjection().check(classes);
  }
}
