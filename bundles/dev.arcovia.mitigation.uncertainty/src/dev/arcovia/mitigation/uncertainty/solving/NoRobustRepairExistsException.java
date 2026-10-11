package dev.arcovia.mitigation.uncertainty.solving;

import java.io.Serial;
import java.util.Optional;

/**
 * Signals that no selection of available actions satisfies the robust ILP for every scenario.
 */
public class NoRobustRepairExistsException extends Exception {
    @Serial
    private static final long serialVersionUID = -1724088526343863172L;

    private final String solverStatus;

    /**
     * Creates an infeasibility exception with the solver's reported status.
     *
     * @param message      the explanation of the infeasible repair problem
     * @param solverStatus the solver status, or {@code null} when it is unavailable
     */
    public NoRobustRepairExistsException(String message, String solverStatus) {
        super(message);
        this.solverStatus = solverStatus;
    }

    /**
     * Returns the solver status captured when infeasibility was detected.
     *
     * @return the solver status or empty when none was supplied
     */
    public Optional<String> solverStatus() {
        return Optional.ofNullable(solverStatus);
    }
}
