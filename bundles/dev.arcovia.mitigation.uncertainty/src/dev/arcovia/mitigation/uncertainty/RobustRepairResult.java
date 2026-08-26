package dev.arcovia.mitigation.uncertainty;

import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;
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
 * @param totalCost                the legacy total-cost alias for the objective value
 * @param costBreakdown            the selected direct and shared cost components
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
        @NonNull ObjectiveCostBreakdown costBreakdown,
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
     * @param totalCost                the final objective value
     * @param costBreakdown            the selected cost components
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
        if (!Double.isFinite(totalCost) || totalCost < 0) {
            throw new IllegalArgumentException("totalCost must be finite and non-negative");
        }
        costBreakdown = Objects.requireNonNull(costBreakdown, "costBreakdown must not be null");
        if (Math.abs(totalCost - costBreakdown.objectiveValue()) > 1e-9) {
            throw new IllegalArgumentException("totalCost must equal costBreakdown objectiveValue");
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
     * Creates a compatibility result with an action-only cost breakdown.
     *
     * @param repairedModel            the repaired base model
     * @param selectedActions          the applied actions
     * @param totalCost                the legacy total cost
     * @param consideredScenarioIds    the considered scenario identifiers
     * @param preRepairViolationCount  the violations before repair
     * @param postRepairViolationCount the violations after repair
     * @param solverStatus             the solver status
     * @param validationStatus         the validation outcome
     */
    public RobustRepairResult(DataFlowDiagramAndDictionary repairedModel, List<ActionTerm> selectedActions,
                              double totalCost, List<String> consideredScenarioIds,
                              int preRepairViolationCount, int postRepairViolationCount,
                              String solverStatus, ValidationStatus validationStatus) {
        this(repairedModel, selectedActions, totalCost, ObjectiveCostBreakdown.legacy(totalCost),
                consideredScenarioIds, preRepairViolationCount, postRepairViolationCount,
                solverStatus, validationStatus);
    }

    /**
     * Returns the declared final objective value.
     *
     * @return \(J_\Theta\) for the selected plan
     */
    public double objectiveValue() {
        return costBreakdown.objectiveValue();
    }

    /**
     * Returns the legacy objective alias.
     *
     * @return the final objective value
     * @deprecated use {@link #objectiveValue()} or {@link #costBreakdown()} instead
     */
    @Deprecated
    public double totalCost() {
        return totalCost;
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
