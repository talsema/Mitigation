package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.ilp.Node;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable stage-4 input for the robust solver.
 */
public record RepairPreparationResult(
        @NonNull List<Constraint> preparedConstraints,
        @NonNull Set<Node> violatingNodes,
        @NonNull List<List<Mitigation>> mitigations,
        @NonNull List<Mitigation> allMitigations,
        @NonNull List<List<Mitigation>> contradictions
) {

    /**
     * Validates and defensively copies the stage-4 preparation artifacts.
     *
     * @param preparedConstraints the copied and augmented repair constraints
     * @param violatingNodes      the nodes that violate at least one prepared constraint
     * @param mitigations         the coverage alternatives for each violation
     * @param allMitigations      all candidate and required mitigation actions
     * @param contradictions      mutually exclusive candidate-action pairs
     */
    public RepairPreparationResult {
        preparedConstraints = List.copyOf(Objects.requireNonNull(preparedConstraints, "preparedConstraints must not be null"));
        violatingNodes = Set.copyOf(Objects.requireNonNull(violatingNodes, "violatingNodes must not be null"));
        mitigations = Objects.requireNonNull(mitigations, "mitigations must not be null").stream()
                .map(List::copyOf)
                .toList();
        allMitigations = List.copyOf(Objects.requireNonNull(allMitigations, "allMitigations must not be null"));
        contradictions = Objects.requireNonNull(contradictions, "contradictions must not be null").stream()
                .map(List::copyOf)
                .toList();
    }
}
