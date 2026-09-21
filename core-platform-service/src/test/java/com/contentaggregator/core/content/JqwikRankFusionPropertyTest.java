package com.contentaggregator.core.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class JqwikRankFusionPropertyTest {

  private final ReciprocalRankFusionCalculator calculator = new ReciprocalRankFusionCalculator(60);

  @Property(tries = 1000)
  @Label("Monotonicity: better or equal ranks in all modalities imply strictly higher RRF score")
  void monotonicityProperty(@ForAll("candidatePairs") CandidatePair pair) {
    double scoreA = calculator.calculateScore(pair.rankA1(), pair.rankA2());
    double scoreB = calculator.calculateScore(pair.rankB1(), pair.rankB2());

    assertThat(scoreA).isGreaterThan(scoreB);
  }

  @Property(tries = 1000)
  @Label("Score Bounds: for 2 modalities with k=60, RRF score must be in (0, 2/61]")
  void scoreBoundsProperty(@ForAll("validRanks") int rank1, @ForAll("validRanks") int rank2) {
    double score = calculator.calculateScore(rank1, rank2);
    double maxPossible = 2.0 / (60.0 + 1.0); // rank 1 in both

    assertThat(score).isGreaterThan(0.0);
    assertThat(score).isLessThanOrEqualTo(maxPossible);
  }

  @Property(tries = 1000)
  @Label("Symmetry: swapping modality ranks produces identical RRF score")
  void symmetryProperty(@ForAll("validRanks") int rank1, @ForAll("validRanks") int rank2) {
    double score1 = calculator.calculateScore(rank1, rank2);
    double score2 = calculator.calculateScore(rank2, rank1);

    assertThat(score1).isCloseTo(score2, within(1e-12));
  }

  @Property(tries = 1000)
  @Label("Determinism: identical input ranks always produce identical RRF score")
  void determinismProperty(@ForAll("validRanks") int rank1, @ForAll("validRanks") int rank2) {
    double first = calculator.calculateScore(rank1, rank2);
    double second = calculator.calculateScore(rank1, rank2);

    assertThat(first).isEqualTo(second);
  }

  @Provide
  Arbitrary<Integer> validRanks() {
    return Arbitraries.integers().between(1, 100);
  }

  @Provide
  Arbitrary<CandidatePair> candidatePairs() {
    return Combinators.combine(
            Arbitraries.integers().between(1, 50),
            Arbitraries.integers().between(1, 50),
            Arbitraries.integers().between(1, 10),
            Arbitraries.integers().between(0, 10))
        .as(
            (rA1, rA2, delta1, delta2) ->
                new CandidatePair(
                    rA1,
                    rA2,
                    rA1 + delta1, // strictly worse in rank 1
                    rA2 + delta2 // worse or equal in rank 2
                    ));
  }

  record CandidatePair(int rankA1, int rankA2, int rankB1, int rankB2) {}
}
