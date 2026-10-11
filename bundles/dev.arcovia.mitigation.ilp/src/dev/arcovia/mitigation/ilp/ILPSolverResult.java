package dev.arcovia.mitigation.ilp;

import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;

import java.util.List;
import java.util.Objects;

/**
 * Result of one baseline ILP solve.
 *
 * @param selectedMitigations the selected repair actions
 * @param solverStatus the SCIP status
 * @param costBreakdown the selected objective-cost breakdown
 */
public record ILPSolverResult(List<Mitigation> selectedMitigations, String solverStatus,
                              ObjectiveCostBreakdown costBreakdown) {

    /**
     * Validates and snapshots the solver result.
     *
     * @param selectedMitigations the selected repair actions
     * @param solverStatus the SCIP status
     * @param costBreakdown the selected objective-cost breakdown
     */
    public ILPSolverResult {
        selectedMitigations = List.copyOf(Objects.requireNonNull(selectedMitigations,
                "selectedMitigations must not be null"));
        solverStatus = Objects.requireNonNull(solverStatus, "solverStatus must not be null");
        costBreakdown = Objects.requireNonNull(costBreakdown, "costBreakdown must not be null");
    }
}
