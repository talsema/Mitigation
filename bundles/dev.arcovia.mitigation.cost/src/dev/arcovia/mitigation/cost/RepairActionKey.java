package dev.arcovia.mitigation.cost;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stable semantic identity of one repair action.
 */
public record RepairActionKey(String domain, ActionType actionType, List<LabelKey> labels)
        implements Comparable<RepairActionKey> {

    /**
     * Validates and snapshots one action identity.
     *
     * @param domain     the affected model element
     * @param actionType the action kind
     * @param labels     the ordered action labels
     */
    public RepairActionKey {
        domain = Objects.requireNonNull(domain, "domain must not be null");
        actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        labels = List.copyOf(Objects.requireNonNull(labels, "labels must not be null"));
    }

    /**
     * Returns a deterministic identifier for configuration and solver variable names.
     *
     * @return the action type, domain, and ordered labels
     */
    public String stableId() {
        return actionType.name() + "|" + domain + "|" + labels.stream()
                .map(LabelKey::stableId)
                .collect(Collectors.joining(","));
    }

    /**
     * Orders action keys deterministically.
     *
     * @param other the key to compare
     * @return the comparison result
     */
    @Override
    public int compareTo(RepairActionKey other) {
        Objects.requireNonNull(other, "other must not be null");
        int actionComparison = actionType.compareTo(other.actionType);
        if (actionComparison != 0) {
            return actionComparison;
        }
        int domainComparison = domain.compareTo(other.domain);
        if (domainComparison != 0) {
            return domainComparison;
        }
        return compareLabels(labels, other.labels);
    }

    /**
     * Compares two ordered label lists.
     *
     * @param left  the first label list
     * @param right the second label list
     * @return the comparison result
     */
    private static int compareLabels(List<LabelKey> left, List<LabelKey> right) {
        int sharedLength = Math.min(left.size(), right.size());
        for (int index = 0; index < sharedLength; index++) {
            int comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    /**
     * Semantic identity of one label carried by an action.
     *
     * @param category the label category
     * @param type     the label type
     * @param value    the label value
     */
    public record LabelKey(String category, String type, String value) implements Comparable<LabelKey> {

        /**
         * Validates one label identity.
         *
         * @param category the label category
         * @param type     the label type
         * @param value    the label value
         */
        public LabelKey {
            category = Objects.requireNonNull(category, "category must not be null");
            type = Objects.requireNonNull(type, "type must not be null");
            value = Objects.requireNonNull(value, "value must not be null");
        }

        /**
         * Returns a deterministic identifier for this label.
         *
         * @return the category, type, and value
         */
        private String stableId() {
            return category + ":" + type + "=" + value;
        }

        /**
         * Orders label keys deterministically.
         *
         * @param other the key to compare
         * @return the comparison result
         */
        @Override
        public int compareTo(LabelKey other) {
            Objects.requireNonNull(other, "other must not be null");
            int categoryComparison = category.compareTo(other.category);
            if (categoryComparison != 0) {
                return categoryComparison;
            }
            int typeComparison = type.compareTo(other.type);
            return typeComparison != 0 ? typeComparison : value.compareTo(other.value);
        }
    }
}
