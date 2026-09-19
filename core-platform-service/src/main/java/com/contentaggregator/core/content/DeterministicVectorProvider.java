package com.contentaggregator.core.content;

import java.nio.charset.StandardCharsets;

public final class DeterministicVectorProvider {

  private DeterministicVectorProvider() {}

  public static float[] generate(String text, int dimension) {
    float[] vector = new float[dimension];
    if (text == null || text.isBlank()) {
      return vector;
    }

    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < dimension; i++) {
      int hash = (i * 31 + bytes[i % bytes.length]) ^ (bytes.length * 17);
      vector[i] = (float) ((Math.abs(hash) % 1000) / 1000.0);
    }

    // Normalize to unit length
    float sumSquares = 0.0f;
    for (float v : vector) {
      sumSquares += v * v;
    }

    if (sumSquares > 0) {
      float norm = (float) Math.sqrt(sumSquares);
      for (int i = 0; i < dimension; i++) {
        vector[i] /= norm;
      }
    }

    return vector;
  }
}
