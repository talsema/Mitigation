package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

/**
 * Holds the frozen expected outcome for one evaluation case.
 *
 * @param verdict           expected comparison verdict
 * @param fullScenarioCount selected-source scenario-product size
 * @param provenance        model origin and adaptation status
 * @param sourceTypes       selected uncertainty-source types
 */
record CaseExpectation(ComparisonVerdict verdict, int fullScenarioCount, String provenance, String sourceTypes) {
}
