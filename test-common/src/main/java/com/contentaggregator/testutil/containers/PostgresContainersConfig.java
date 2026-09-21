package com.contentaggregator.testutil.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL container, переиспользуемый между Spring-контекстами и
 * Gradle-форками. Симметричный паттерн с {@link KafkaContainersConfig}.
 *
 * <p>См. подробное обоснование в {@link KafkaContainersConfig} (singleton,
 * withReuse, destroyMethod="").
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainersConfig {

  // Контейнер живёт всё время JVM — закрывает Ryuk.
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
      new PostgreSQLContainer<>(TestImages.POSTGRES)
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test")
          .withReuse(true);

  static {
    POSTGRES_CONTAINER.start();
  }

  @Bean(destroyMethod = "")
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return POSTGRES_CONTAINER;
  }
}
