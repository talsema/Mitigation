package dev.arcovia.mitigation.uncertainty.validation;

import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Aggregated validation result. Pipeline stage 7.
 *
 * @param scenarios the validation result for each scenario
 */
public record RobustRepairValidationResult(@NonNull List<ScenarioValidation> scenarios) {
    /**
     * Validates and snapshots the scenario-level validation outcomes.
     *
     * @param scenarios the validation outcome for each considered scenario
     */
    public RobustRepairValidationResult {
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios must not be null"));
    }

    /**
     * @return the number of validated scenarios
     */
    public int scenarioCount() {
        return scenarios.size();
    }

    /**
     * @return the total number of residual targeted violations
     */
    public int totalViolationCount() {
        return scenarios.stream()
                .mapToInt(ScenarioValidation::violationCount)
                .sum();
    }

    /**
     * @return {@code true} when no scenario has a residual targeted violation
     */
    public boolean passed() {
        return totalViolationCount() == 0;
    }

    /**
     * @return the validated scenario identifiers
     */
    public List<String> scenarioIds() {
        return scenarios.stream()
                .map(ScenarioValidation::scenarioId)
                .toList();
    }

    /**
     * One scenario's validation outcome.
     *
     * @param scenarioId     the scenario id
     * @param violationCount residual targeted violations in that scenario
     */
    public record ScenarioValidation(@NonNull String scenarioId, int violationCount) {
        /**
         * Validates one scenario's residual violation count.
         *
         * @param scenarioId     the scenario identifier
         * @param violationCount the non-negative number of residual targeted violations
         * @throws IllegalArgumentException if {@code violationCount} is negative
         */
        public ScenarioValidation {
            scenarioId = Objects.requireNonNull(scenarioId, "scenarioId must not be null");
            if (violationCount < 0) {
                throw new IllegalArgumentException("violationCount must not be negative");
            }
        }

        /**
         * Determines if the scenario is free of targeted violations.
         *
         * @return true if there are no residual targeted violations in the scenario,
         * false otherwise.
         */
        public boolean violationFree() {
            return violationCount == 0;
        }
    }
}
