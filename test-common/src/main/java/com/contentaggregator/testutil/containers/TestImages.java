package com.contentaggregator.testutil.containers;

import org.testcontainers.utility.DockerImageName;

/** Single source of truth for Docker images used in Testcontainers. */
public final class TestImages {

  /** Apache Kafka in KRaft mode (no Zookeeper). */
  public static final DockerImageName KAFKA = DockerImageName.parse("apache/kafka:4.1.1");

  public static final DockerImageName REDPANDA =
      DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.14");

  /** PostgreSQL 16 with pgvector 0.7+ extension support. */
  public static final DockerImageName POSTGRES =
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

  /** Redis 7 for caching and XFetch. */
  public static final DockerImageName REDIS = DockerImageName.parse("redis:7-alpine");

  private TestImages() {
    // utility class
  }
}
