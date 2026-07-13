package dev.arcovia.mitigation.uncertainty.validation;

import dev.arcovia.mitigation.uncertainty.RobustRepairResult;
import org.eclipse.jdt.annotation.NonNull;

import java.io.Serial;
import java.util.Objects;

/**
 * Thrown when stage 7 re-analysis finds that the applied repair still leaves a targeted violation
 * in some scenario, i.e., the solver-feasible plan failed architecture-level validation. Carries the
 * {@link RobustRepairResult} so the caller can inspect the residual violations.
 */
public class RobustRepairValidationException extends Exception {
    @Serial
    private static final long serialVersionUID = -2164464825541923698L;

    private final RobustRepairResult repairResult;

    /**
     * Creates an exception that retains the failed repair result for diagnosis.
     *
     * @param repairResult the failed repair result carrying per-scenario residual violations
     */
    public RobustRepairValidationException(@NonNull RobustRepairResult repairResult) {
        super("Robust repair validation failed: " + repairResult.postRepairViolationCount()
              + " targeted violation(s) remain after repair across "
              + repairResult.consideredScenarioCount() + " considered scenario(s).");
        this.repairResult = Objects.requireNonNull(repairResult, "repairResult must not be null");
    }

    /**
     * @return the failed repair result
     */
    public RobustRepairResult repairResult() {
        return repairResult;
    }
}
