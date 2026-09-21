package com.contentaggregator.testutil.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

public final class CleanArchitectureRules {

  /**
   * Controllers must never inject repositories directly.
   * Business logic and data access must be mediated through services.
   */
  public static final ArchRule CONTROLLERS_MUST_NOT_DEPEND_ON_REPOSITORIES =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .because("Controllers must not bypass the service layer to access repositories directly");

  /**
   * Strict ban on Project Lombok in SOTA 2026.
   * Modern Java 21+ records, pattern matching, and explicit constructors eliminate the need for Lombok.
   */
  public static final ArchRule NO_LOMBOK_ALLOWED =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("lombok..")
          .because("Lombok is prohibited in SOTA 2026 architecture; use Java 21 records and standard constructors");

  /**
   * Services must encapsulate business logic and never execute raw SQL directly via JdbcTemplate.
   * Direct SQL execution is strictly reserved for Repository layer.
   */
  public static final ArchRule SERVICES_MUST_NOT_EXECUTE_SQL =
      noClasses()
          .that()
          .haveSimpleNameEndingWith("Service")
          .or()
          .haveSimpleNameEndingWith("ServiceImpl")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
          .because("Services must not execute raw SQL directly; encapsulate database operations in repositories");

  /**
   * Prohibit legacy helper libraries (Guava, Apache Commons) in favor of modern Java 21+ standard library.
   */
  public static final ArchRule PROHIBIT_GUAVA_AND_COMMONS =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.google.common..", "org.apache.commons..")
          .because("Leverage modern Java 21+ standard library APIs instead of transient helper libraries (Guava, Apache Commons)");

  private CleanArchitectureRules() {}
}
