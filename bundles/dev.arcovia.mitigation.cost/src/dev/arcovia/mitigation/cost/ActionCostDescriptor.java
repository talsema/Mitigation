package dev.arcovia.mitigation.cost;

import java.util.Objects;

/**
 * Pre-computed cost descriptors for one canonical repair action.
 */
public record ActionCostDescriptor(
        RepairActionKey actionKey,
        double impact,
        double ripple,
        double structuralMagnitude,
        double assuranceActivities,
        double baseCost,
        double impactCost,
        double rippleCost,
        double structuralCost,
        double assuranceCost,
        double directCost,
        boolean overridden
) {

    /**
     * Validates a descriptor and all of its non-negative numeric values.
     *
     * @param actionKey           the canonical action identity
     * @param impact              the directly affected DFD elements and flows
     * @param ripple              the downstream reachability footprint
     * @param structuralMagnitude the structural change magnitude
     * @param assuranceActivities the assurance activity count
     * @param baseCost            the type-specific base cost
     * @param impactCost          the weighted impact contribution
     * @param rippleCost          the weighted ripple contribution
     * @param structuralCost      the weighted structural contribution
     * @param assuranceCost       the weighted assurance contribution
     * @param directCost          the final direct action cost
     * @param overridden          whether an action-specific direct-cost override was used
     */
    public ActionCostDescriptor {
        actionKey = Objects.requireNonNull(actionKey, "actionKey must not be null");
        validate(impact, "impact");
        validate(ripple, "ripple");
        validate(structuralMagnitude, "structuralMagnitude");
        validate(assuranceActivities, "assuranceActivities");
        validate(baseCost, "baseCost");
        validate(impactCost, "impactCost");
        validate(rippleCost, "rippleCost");
        validate(structuralCost, "structuralCost");
        validate(assuranceCost, "assuranceCost");
        validate(directCost, "directCost");
    }

    /**
     * Validates one non-negative finite metric.
     *
     * @param value the metric value
     * @param name  the metric name
     */
    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
