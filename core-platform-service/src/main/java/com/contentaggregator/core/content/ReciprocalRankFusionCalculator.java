package com.contentaggregator.core.content;

public class ReciprocalRankFusionCalculator {

  private final int kConstant;

  public ReciprocalRankFusionCalculator() {
    this(60);
  }

  public ReciprocalRankFusionCalculator(int kConstant) {
    this.kConstant = kConstant;
  }

  public double calculateScore(int rank1, int rank2) {
    return (1.0 / (kConstant + rank1)) + (1.0 / (kConstant + rank2));
  }

  public double calculateScore(Integer rank1, Integer rank2) {
    double score = 0.0;
    if (rank1 != null && rank1 > 0) {
      score += 1.0 / (kConstant + rank1);
    }
    if (rank2 != null && rank2 > 0) {
      score += 1.0 / (kConstant + rank2);
    }
    return score;
  }
}
