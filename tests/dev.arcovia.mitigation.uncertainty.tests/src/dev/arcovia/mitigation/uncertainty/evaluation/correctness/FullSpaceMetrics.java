package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

import java.util.List;

/**
 * Holds the outcome of validating a model across the selected scenario space.
 *
 * @param totalViolations   accumulated violation count
 * @param violatedScenarios number of scenarios containing a violation
 * @param scenarioIds       independently validated scenario identifiers
 * @param passed            whether every checked scenario satisfies its constraints
 */
record FullSpaceMetrics(int totalViolations, long violatedScenarios, List<String> scenarioIds, boolean passed) {

    static FullSpaceMetrics notApplicable() {
        return new FullSpaceMetrics(-1, -1, List.of(), false);
    }
}
