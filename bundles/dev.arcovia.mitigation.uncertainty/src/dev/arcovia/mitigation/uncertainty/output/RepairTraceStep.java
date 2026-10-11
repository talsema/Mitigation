package dev.arcovia.mitigation.uncertainty.output;

import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;

/**
 * One recorded step of the repair-execution trace (package-private; created via
 * {@link RepairOutput#record}).
 *
 * @param sequence 1-based position of the step in the trace
 * @param name     short step name (e.g. {@code prepare-scenarios})
 * @param detail   human-readable detail line for the step
 */
record RepairTraceStep(int sequence, @NonNull String name, @NonNull String detail) {
    /**
     * Validates one ordered trace entry.
     *
     * @param sequence the one-based sequence number; must be positive
     * @param name     the non-blank step name
     * @param detail   the recorded detail
     * @throws IllegalArgumentException if the sequence is not positive or the name is blank
     */
    public RepairTraceStep {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        name = Objects.requireNonNull(name, "name must not be null").trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        detail = Objects.requireNonNull(detail, "detail must not be null");
    }
}
