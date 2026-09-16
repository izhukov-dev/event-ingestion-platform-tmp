package com.contentaggregator.core.enrichment;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

  @Bean
  public CommonErrorHandler commonErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> {
              log.error("Poison pill detected for topic {}. Routing to DLT.", record.topic(), ex);
              return new TopicPartition(record.topic() + ".DLT", record.partition());
            });

    // 2 retries with 1 second backoff before sending to DLT
    FixedBackOff backOff = new FixedBackOff(1000L, 2L);
    return new DefaultErrorHandler(recoverer, backOff);
  }
}
