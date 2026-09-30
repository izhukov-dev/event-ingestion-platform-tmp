package com.contentaggregator.core.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.contentaggregator.testutil.TestTags;
import com.contentaggregator.testutil.architecture.CleanArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

@Tag(TestTags.UNIT)
@Tag(TestTags.FAST)
class CleanArchitectureTest {

  private final JavaClasses coreClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.contentaggregator.core");

  @Test
  @DisplayName("Controllers must never inject Repositories directly")
  void controllersMustNotDependOnRepositories() {
    CleanArchitectureRules.CONTROLLERS_MUST_NOT_DEPEND_ON_REPOSITORIES.check(coreClasses);
  }

  @Test
  @DisplayName("No classes should depend on Lombok")
  void noLombokAllowed() {
    CleanArchitectureRules.NO_LOMBOK_ALLOWED.check(coreClasses);
  }

  @Test
  @DisplayName("Services must never execute raw SQL directly via JdbcTemplate")
  void servicesMustNotExecuteSql() {
    CleanArchitectureRules.SERVICES_MUST_NOT_EXECUTE_SQL.check(coreClasses);
  }

  @Test
  @DisplayName("No classes should depend on Guava or Apache Commons")
  void prohibitGuavaAndCommons() {
    CleanArchitectureRules.PROHIBIT_GUAVA_AND_COMMONS.check(coreClasses);
  }

  @Test
  @DisplayName("Service implementations should not depend on Controllers")
  void servicesMustNotDependOnControllers() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Service")
        .or()
        .haveSimpleNameEndingWith("ServiceImpl")
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Controller")
        .because("Service layer must not depend on web layer")
        .check(coreClasses);
  }

  @Test
  @DisplayName("Integration tests must never declare @MockBean or @MockitoBean fields")
  void noMockBeanInIntegrationTests() {
    JavaClasses testClasses =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
            .importPackages("com.contentaggregator.core");
    CleanArchitectureRules.NO_MOCKBEAN_IN_INTEGRATION_TESTS.check(testClasses);
  }
}
