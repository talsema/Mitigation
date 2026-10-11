package dev.arcovia.mitigation.cost;

import java.util.Objects;
import java.util.Set;

/**
 * A fixed-cost investment shared by explicitly listed repair actions.
 */
public record SharedCostGroup(String id, double fixedCost, Set<RepairActionKey> memberActions) {

    /**
     * Validates and snapshots one shared-cost group.
     *
     * @param id            the stable group identifier
     * @param fixedCost     the non-negative fixed cost
     * @param memberActions the actions that require the investment
     */
    public SharedCostGroup {
        if (Objects.requireNonNull(id, "id must not be null").isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (!Double.isFinite(fixedCost) || fixedCost < 0) {
            throw new IllegalArgumentException("fixedCost must be finite and non-negative");
        }
        memberActions = Set.copyOf(Objects.requireNonNull(memberActions, "memberActions must not be null"));
        if (memberActions.isEmpty()) {
            throw new IllegalArgumentException("memberActions must not be empty");
        }
    }
}
