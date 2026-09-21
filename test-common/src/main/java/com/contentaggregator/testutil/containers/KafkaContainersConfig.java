package com.contentaggregator.testutil.containers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Singleton Kafka container, переиспользуемый между Spring-контекстами и Gradle-форками.
 *
 * <p><b>Зачем singleton.</b> Дефолтный паттерн ({@code @Bean} без статики) поднимает
 * по контейнеру на каждый @SpringBootTest. При параллельных JVM-форках это
 * приводит к race condition apache/kafka image на копирующемся
 * {@code /tmp/testcontainers_start.sh} ("Text file busy").
 *
 * <p><b>Зачем withReuse(true).</b> Включает Testcontainers reuse: на хосте
 * выживает один контейнер на хеш конфигурации, остальные форки/прогоны его
 * подцепляют. Требует {@code testcontainers.reuse.enable=true} (выставляется в
 * build.gradle для всех Test-тасков) и опционально
 * {@code ~/.testcontainers.properties} с тем же флагом.
 *
 * <p><b>Зачем destroyMethod="".</b> Запрещает Spring останавливать контейнер при
 * закрытии контекста — иначе следующий тест получит уже мёртвый Kafka. Очисткой
 * на выходе JVM занимается Ryuk-контейнер Testcontainers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainersConfig {

  // Контейнер живёт всё время JVM — закрывает Ryuk; явный close()
  // здесь нежелателен (следующий тест получил бы мёртвый Kafka).
  @SuppressWarnings("resource")
  private static final KafkaContainer KAFKA_CONTAINER =
      new KafkaContainer(TestImages.KAFKA).withReuse(true);

  static {
    KAFKA_CONTAINER.start();
  }

  @Bean(destroyMethod = "")
  @ServiceConnection
  KafkaContainer kafkaContainer() {
    return KAFKA_CONTAINER;
  }
}
