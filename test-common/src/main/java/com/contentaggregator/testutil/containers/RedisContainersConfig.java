package com.contentaggregator.testutil.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * Singleton Redis container for Testcontainers integration testing.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisContainersConfig {

  @SuppressWarnings("resource")
  private static final GenericContainer<?> REDIS_CONTAINER =
      new GenericContainer<>(TestImages.REDIS).withExposedPorts(6379).withReuse(true);

  static {
    REDIS_CONTAINER.start();
  }

  @Bean(destroyMethod = "")
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return REDIS_CONTAINER;
  }
}
