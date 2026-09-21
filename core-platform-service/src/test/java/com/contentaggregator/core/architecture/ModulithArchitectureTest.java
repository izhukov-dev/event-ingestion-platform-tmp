package com.contentaggregator.core.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.contentaggregator.annotations.UnitTest;
import com.contentaggregator.core.CorePlatformApplication;

@UnitTest
class ModulithArchitectureTest {

  private final ApplicationModules modules = ApplicationModules.of(CorePlatformApplication.class);

  @Test
  @DisplayName("Verify Spring Modulith module boundaries and enforce acyclic architecture")
  void verifyModularArchitecture() {
    modules.verify();
  }
}
