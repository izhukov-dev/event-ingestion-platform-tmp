package com.contentaggregator.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.contentaggregator.ingestion.crawler.IngestionCrawlerProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IngestionCrawlerProperties.class)
public class IngestionApplication {

  public static void main(String[] args) {
    SpringApplication.run(IngestionApplication.class, args);
  }
}
