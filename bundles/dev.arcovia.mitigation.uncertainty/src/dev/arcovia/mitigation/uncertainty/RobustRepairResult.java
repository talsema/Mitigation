package dev.arcovia.mitigation.uncertainty;

import dev.arcovia.mitigation.ilp.ActionTerm;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Result of one robust repair.
 *
 * @param repairedModel            the repaired base model
 * @param selectedActions          the applied actions
 * @param totalCost                the total action cost
 * @param consideredScenarioIds    the considered scenario identifiers
 * @param preRepairViolationCount  the number of violations before repair
 * @param postRepairViolationCount the number of violations after repair
 * @param solverStatus             the ILP solver status
 * @param validationStatus         the validation outcome
 */
public record RobustRepairResult(
        @NonNull DataFlowDiagramAndDictionary repairedModel,
        @NonNull List<ActionTerm> selectedActions,
        double totalCost,
        @NonNull List<String> consideredScenarioIds,
        int preRepairViolationCount,
        int postRepairViolationCount,
        @NonNull String solverStatus,
        @NonNull ValidationStatus validationStatus) {

    /**
     * Validates the result metrics and snapshots the selected actions and scenario identifiers.
     *
     * @param repairedModel            the repaired base model
     * @param selectedActions          the applied action terms
     * @param totalCost                the non-negative total action cost
     * @param consideredScenarioIds    the scenario identifiers considered during repair
     * @param preRepairViolationCount  the non-negative count before repair
     * @param postRepairViolationCount the non-negative count after repair
     * @param solverStatus             the solver status
     * @param validationStatus         the architecture-validation status
     * @throws IllegalArgumentException if a cost or violation count is negative
     */
    public RobustRepairResult {
        Objects.requireNonNull(repairedModel, "repairedModel must not be null");
        selectedActions = List.copyOf(Objects.requireNonNull(selectedActions, "selectedActions must not be null"));
        if (totalCost < 0) {
            throw new IllegalArgumentException("totalCost must not be negative");
        }
        consideredScenarioIds = List.copyOf(
                Objects.requireNonNull(consideredScenarioIds, "consideredScenarioIds must not be null"));
        if (preRepairViolationCount < 0) {
            throw new IllegalArgumentException("preRepairViolationCount must not be negative");
        }
        if (postRepairViolationCount < 0) {
            throw new IllegalArgumentException("postRepairViolationCount must not be negative");
        }
        solverStatus = Objects.requireNonNull(solverStatus, "solverStatus must not be null");
        validationStatus = Objects.requireNonNull(validationStatus, "validationStatus must not be null");
    }

    /**
     * Returns the number of scenarios that were considered during the repair process.
     *
     * @return the total count of considered scenario IDs
     */
    public int consideredScenarioCount() {
        return consideredScenarioIds.size();
    }

    /**
     * Checks whether the validation status indicates that the repair process has passed.
     *
     * @return true if the validation status is {@code ValidationStatus.PASSED}, false otherwise
     */
    public boolean validationPassed() {
        return validationStatus == ValidationStatus.PASSED;
    }

    /**
     * Outcome of post-repair validation.
     */
    public enum ValidationStatus {
        PASSED,
        FAILED
    }
}
