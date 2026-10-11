package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.MitigationStrategy;
import dev.arcovia.mitigation.ilp.MitigationType;
import dev.arcovia.mitigation.sat.CompositeLabel;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Prepares and analyzes cloned repair constraints. Pipeline stage 4.
 */
public final class ConstraintPreparation {

    /**
     * Prepares a deep copy of the given list of constraints and analyzes them for further processing.
     *
     * @param constraints the constraints to prepare
     * @return a new list containing deep copies of the original constraints after analysis.
     */
    public List<Constraint> prepare(@NonNull List<Constraint> constraints) {
        Objects.requireNonNull(constraints, "constraints must not be null");
        List<Constraint> preparedConstraints = constraints.stream()
                .map(Constraint::copy)
                .toList();
        analyseConstraints(preparedConstraints);
        return preparedConstraints;
    }

    /**
     * Analyzes the given list of constraints and adjusts their associated mitigation strategies
     * by adding additional constraints or prerequisite mitigation strategies based on the type
     * and labels of each mitigation strategy.
     *
     * @param constraints the constraints to analyze. Each constraint
     *                    may have one or more mitigation strategies that are processed during this
     *                    method execution.
     */
    private void analyseConstraints(List<Constraint> constraints) {
        constraints.stream()
                .flatMap(constraint -> constraint.getMitigations().stream())
                .forEach(mitigation -> {
                    if (isDeletion(mitigation.type())) {
                        setAdditionalConstraints(mitigation, constraints);
                    } else {
                        var requirements = requiredCombinations(mitigation.labels(), constraints);
                        if (!requirements.isEmpty()) {
                            mitigation.addRequired(requirements);
                        }
                    }
                });
    }

    /**
     * Marks deletion strategies that would invalidate another strategy.
     *
     * @param mitigation  the deletion strategy
     * @param constraints the prepared constraints to inspect
     */
    private void setAdditionalConstraints(MitigationStrategy mitigation, List<Constraint> constraints) {
        constraints.stream()
                .filter(constraint -> constraint.getMitigations().stream()
                        .anyMatch(current -> !isDeletion(current.type())
                                             && current.labels().equals(mitigation.labels())))
                .forEach(mitigation::addConstraint);
    }

    /**
     * Builds the alternative action combinations required by all matching preconditions.
     *
     * @param labels      the labels modified by the candidate mitigation
     * @param constraints the prepared constraints that may declare those labels as preconditions
     * @return the combinations of compatible prerequisite strategies, or an empty list when none apply
     */
    private List<List<MitigationStrategy>> requiredCombinations(List<CompositeLabel> labels, List<Constraint> constraints) {
        List<List<MitigationStrategy>> required = List.of();
        for (var label : labels) {
            for (var constraint : constraints) {
                if (!constraint.isPrecondition(label)) {
                    continue;
                }
                List<MitigationStrategy> alternatives = constraint.getMitigations().stream()
                        .filter(mitigation -> !mitigation.labels().contains(label))
                        .toList();
                required = addRequirement(required, alternatives);
            }
        }
        return required;
    }

    /**
     * Extends each existing prerequisite combination with one eligible strategy from a new clause.
     *
     * @param required     combinations accumulated from preceding clauses
     * @param alternatives eligible strategies for the current clause
     * @return the Cartesian extension of {@code required} by {@code alternatives}
     */
    private static List<List<MitigationStrategy>> addRequirement(
            List<List<MitigationStrategy>> required,
            List<MitigationStrategy> alternatives
    ) {
        if (required.isEmpty()) {
            return alternatives.stream().map(List::of).toList();
        }
        return required.stream()
                .flatMap(combination -> alternatives.stream()
                        .map(alternative -> Stream.concat(combination.stream(), Stream.of(alternative)).toList()))
                .toList();
    }

    /**
     * Determines whether a mitigation type removes an existing architectural element or label.
     *
     * @param type the mitigation type to classify
     * @return {@code true} for deletion types; {@code false} otherwise
     */
    private static boolean isDeletion(MitigationType type) {
        return switch (type) {
            case DeleteNodeLabel, DeleteDataLabel, DeleteNode, DeleteFlow -> true;
            default -> false;
        };
    }
}
