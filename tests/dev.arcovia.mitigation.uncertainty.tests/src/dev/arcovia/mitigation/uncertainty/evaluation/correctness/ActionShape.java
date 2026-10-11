package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Describes the shape of one selected repair plan.
 *
 * @param additive    label-addition actions
 * @param subtractive label-removal actions
 * @param structural  graph-changing actions
 */
record ActionShape(int additive, int subtractive, int structural) {

    /**
     * Counts the selected actions in a cost breakdown by their repair shape.
     *
     * @param breakdown the selected-action cost breakdown, or {@code null} when no plan exists
     * @return the corresponding action-shape counts
     */
    static ActionShape of(ObjectiveCostBreakdown breakdown) {
        if (breakdown == null) {
            return new ActionShape(0, 0, 0);
        }
        int adding = 0;
        int removing = 0;
        int structural = 0;
        for (var descriptor : breakdown.selectedActionCosts()) {
            switch (descriptor.actionKey().actionType()) {
                case Adding -> adding++;
                case Removing -> removing++;
                default -> structural++;
            }
        }
        return new ActionShape(adding, removing, structural);
    }

    /**
     * Formats the shape as additive, subtractive, and structural counts.
     *
     * @return the compact report representation
     */
    @Override
    @NonNull
    public String toString() {
        return additive + "/" + subtractive + "/" + structural;
    }
}
