package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

/**
 * Names the fixed outcomes recorded for either repair arm.
 */
enum RepairOutcome {
    VALID_REPAIR,
    VALIDATION_INCOMPLETE,
    VALIDATION_FAILED,
    RESIDUAL_VIOLATIONS,
    NO_ROBUST_REPAIR,
    EXECUTION_ERROR
}
