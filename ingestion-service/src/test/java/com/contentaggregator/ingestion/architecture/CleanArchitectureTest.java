package com.contentaggregator.ingestion.architecture;

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

  private final JavaClasses ingestionClasses =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.contentaggregator.ingestion");

  @Test
  @DisplayName("No classes should depend on Lombok")
  void noLombokAllowed() {
    CleanArchitectureRules.NO_LOMBOK_ALLOWED.check(ingestionClasses);
  }

  @Test
  @DisplayName("No classes should depend on Guava or Apache Commons")
  void prohibitGuavaAndCommons() {
    CleanArchitectureRules.PROHIBIT_GUAVA_AND_COMMONS.check(ingestionClasses);
  }

  @Test
  @DisplayName("Ingestion service must not depend on Web MVC controllers")
  void ingestionMustNotDependOnControllers() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveSimpleNameEndingWith("Controller")
        .because("Ingestion service is purely asynchronous/scheduled and has no web controllers")
        .check(ingestionClasses);
  }
}
