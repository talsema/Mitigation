package dev.arcovia.mitigation.cost;

import java.util.List;
import java.util.Objects;

/**
 * Cost breakdown of one selected repair plan.
 */
public record ObjectiveCostBreakdown(
        String specificationId,
        String specificationVersion,
        String unit,
        List<ActionCostDescriptor> selectedActionCosts,
        List<String> selectedSharedCostGroupIds,
        double directActionCost,
        double sharedEnablerCost,
        double objectiveValue
) {

    /**
     * Validates and snapshots the selected action and group costs.
     *
     * @param specificationId            the applied specification identifier
     * @param specificationVersion       the applied specification version
     * @param unit                       the declared cost unit
     * @param selectedActionCosts        the selected action descriptors
     * @param selectedSharedCostGroupIds the selected shared groups
     * @param directActionCost           the direct-action total
     * @param sharedEnablerCost          the shared-enabler total
     * @param objectiveValue             the final objective value
     */
    public ObjectiveCostBreakdown {
        specificationId = requireText(specificationId, "specificationId");
        specificationVersion = requireText(specificationVersion, "specificationVersion");
        unit = requireText(unit, "unit");
        selectedActionCosts = List.copyOf(Objects.requireNonNull(selectedActionCosts,
                "selectedActionCosts must not be null"));
        selectedSharedCostGroupIds = List.copyOf(Objects.requireNonNull(selectedSharedCostGroupIds,
                "selectedSharedCostGroupIds must not be null"));
        validate(directActionCost, "directActionCost");
        validate(sharedEnablerCost, "sharedEnablerCost");
        validate(objectiveValue, "objectiveValue");
    }

    /**
     * Creates a compatibility breakdown for a legacy action-only total.
     *
     * @param totalCost the legacy total cost
     * @return a labelled compatibility breakdown
     */
    public static ObjectiveCostBreakdown legacy(double totalCost) {
        return new ObjectiveCostBreakdown("legacy-action-cost", "1", "preference-score", List.of(), List.of(),
                totalCost, 0, totalCost);
    }

    /**
     * Requires non-blank metadata.
     *
     * @param value the metadata value
     * @param name  the metadata field name
     * @return the validated value
     */
    private static String requireText(String value, String name) {
        if (Objects.requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    /**
     * Validates one non-negative finite total.
     *
     * @param value the total value
     * @param name  the total name
     */
    private static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
