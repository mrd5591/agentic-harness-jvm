package io.harness.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.GeneralCodingRules;
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

  /**
   * Constructor injection only. ArchUnit's own rule knows every injection annotation (Spring's
   * {@code @Autowired} and {@code @Value}, {@code @Inject}, {@code @Resource}), so it is used
   * rather than a one-annotation copy that would pass a field injected some other way.
   */
  static ArchRule noFieldInjection() {
    return GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
        .because("constructor injection keeps collaborator count visible and the class testable")
        .allowEmptyShould(true);
  }

  /**
   * Run every layering rule against a service laid out under one base package: model in {@code
   * <base>.domain}, delivery in {@code <base>.api}, and one slice per direct subpackage.
   */
  static void checkAll(JavaClasses classes, String basePackage) {
    domainDoesNotDependOnApi(basePackage + ".domain..", basePackage + ".api..").check(classes);
    noCyclesBetweenSlices(basePackage + ".(*)..").check(classes);
    noFieldInjection().check(classes);
  }
}
