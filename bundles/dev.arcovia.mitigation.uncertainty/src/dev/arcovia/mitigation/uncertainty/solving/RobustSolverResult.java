package dev.arcovia.mitigation.uncertainty.solving;

import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;
import dev.arcovia.mitigation.ilp.Mitigation;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Result of one robust ILP solve.
 *
 * @param selectedMitigations the selected canonical actions
 * @param solverStatus        the solver status
 * @param variableCount       the number of solver variables
 * @param constraintCount     the number of solver constraints
 * @param costBreakdown       the selected direct and shared objective costs
 */
public record RobustSolverResult(
        @NonNull List<Mitigation> selectedMitigations,
        @NonNull String solverStatus,
        int variableCount,
        int constraintCount,
        @NonNull ObjectiveCostBreakdown costBreakdown
) {
    /**
     * Validates the solver result and snapshots the selected actions.
     *
     * @param selectedMitigations the selected canonical mitigations
     * @param solverStatus        the solver status string
     * @param variableCount       the non-negative number of solver variables
     * @param constraintCount     the non-negative number of solver constraints
     * @param costBreakdown       the selected objective-cost breakdown
     * @throws IllegalArgumentException if a count is negative
     */
    public RobustSolverResult {
        selectedMitigations = List.copyOf(
                Objects.requireNonNull(selectedMitigations, "selectedMitigations must not be null")
        );
        solverStatus = Objects.requireNonNull(solverStatus, "solverStatus must not be null");
        if (variableCount < 0) {
            throw new IllegalArgumentException("variableCount must not be negative");
        }
        if (constraintCount < 0) {
            throw new IllegalArgumentException("constraintCount must not be negative");
        }
        costBreakdown = Objects.requireNonNull(costBreakdown, "costBreakdown must not be null");
    }

    /**
     * Creates a compatibility result from an action-only total.
     *
     * @param selectedMitigations the selected canonical mitigations
     * @param solverStatus        the solver status string
     * @param variableCount       the non-negative number of solver variables
     * @param constraintCount     the non-negative number of solver constraints
     */
    public RobustSolverResult(List<Mitigation> selectedMitigations, String solverStatus, int variableCount,
                              int constraintCount) {
        this(selectedMitigations, solverStatus, variableCount, constraintCount,
                ObjectiveCostBreakdown.legacy(selectedMitigations.stream().mapToDouble(Mitigation::cost).sum()));
    }

    /**
     * Returns the selected objective value.
     *
     * @return the declared \(J_\Theta\) value
     */
    public double objectiveValue() {
        return costBreakdown.objectiveValue();
    }
}
