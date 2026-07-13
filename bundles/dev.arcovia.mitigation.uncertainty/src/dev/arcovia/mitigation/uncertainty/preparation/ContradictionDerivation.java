package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.ActionType;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.sat.CompositeLabel;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Derives conflicting add/remove mitigation pairs. Pipeline stage 4.
 */
public final class ContradictionDerivation {

    /**
     * Finds conflicting add/remove pairs.
     *
     * @param canonicalMitigations the actions to inspect
     * @return pairs with the same domain and a shared label
     */
    public List<List<Mitigation>> derive(@NonNull Collection<Mitigation> canonicalMitigations) {
        Objects.requireNonNull(canonicalMitigations, "canonicalMitigations must not be null");
        return canonicalMitigations.stream()
                .filter(mitigation -> isRemoval(mitigation.mitigation().type()))
                .flatMap(removal -> contradictionsOf(removal, canonicalMitigations))
                .toList();
    }

    /**
     * Finds additions that conflict with one removal.
     *
     * @param removal        the removal action
     * @param allMitigations the actions to inspect
     * @return matching removal-and-addition pairs
     */
    private Stream<List<Mitigation>> contradictionsOf(Mitigation removal, Collection<Mitigation> allMitigations) {
        return allMitigations.stream()
                .filter(mitigation -> mitigation.mitigation().domain().equals(removal.mitigation().domain()))
                .filter(mitigation -> isAddition(mitigation.mitigation().type()))
                .filter(mitigation -> sharesLabel(removal, mitigation))
                .map(mitigation -> List.of(removal, mitigation));
    }

    /**
     * Determines whether two mitigations share at least one common composite label.
     *
     * @param left  the first mitigation to compare
     * @param right the second mitigation to compare
     * @return {@code true} if the two mitigations share at least one composite label; {@code false} otherwise
     */
    private boolean sharesLabel(Mitigation left, Mitigation right) {
        List<CompositeLabel> leftLabels = left.mitigation().compositeLabels();
        List<CompositeLabel> rightLabels = right.mitigation().compositeLabels();
        if (leftLabels == null || rightLabels == null) {
            return false;
        }
        return leftLabels.stream().anyMatch(rightLabels::contains);
    }

    /**
     * Classifies action terms that remove an existing model element or label.
     *
     * @param type the action type to classify
     * @return {@code true} when the action removes something; {@code false} otherwise
     */
    private static boolean isRemoval(ActionType type) {
        return switch (type) {
            case Removing, RemoveNode, RemoveFlow -> true;
            default -> false;
        };
    }

    /**
     * Classifies action terms that add an element, label, or sink.
     *
     * @param type the action type to classify
     * @return {@code true} when the action adds something; {@code false} otherwise
     */
    private static boolean isAddition(ActionType type) {
        return switch (type) {
            case Adding, AddNode, AddSink -> true;
            default -> false;
        };
    }
}
