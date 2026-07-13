package dev.arcovia.mitigation.uncertainty.solving;

import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.ActionType;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.sat.CompositeLabel;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The semantic identity of a repair action: two actions are equal iff they share the same domain,
 * action type, and ordered label keys. This shared identity is what lets the same architectural
 * change collapse to a single ILP decision variable across scenarios.
 *
 * @param domain     the affected architectural element
 * @param actionType the kind of action (e.g., add/remove label, structural edit)
 * @param labels     the ordered semantic label keys carried by the action
 */
public record RepairActionKey(@NonNull String domain, @NonNull ActionType actionType,
                              @NonNull List<LabelKey> labels)
        implements Comparable<RepairActionKey> {

    /**
     * Validates the canonical action identity and snapshots its ordered labels.
     *
     * @param domain     the affected architectural element
     * @param actionType the action kind
     * @param labels     the ordered semantic labels
     */
    public RepairActionKey {
        domain = Objects.requireNonNull(domain, "domain must not be null");
        actionType = Objects.requireNonNull(actionType, "actionType must not be null");
        labels = List.copyOf(Objects.requireNonNull(labels, "labels must not be null"));
    }

    /**
     * Converts a given {@code Mitigation} object into a {@code RepairActionKey}.
     *
     * @param mitigation the mitigation to convert
     * @return the corresponding {@code RepairActionKey} derived from the input {@code Mitigation}
     */
    public static RepairActionKey from(@NonNull Mitigation mitigation) {
        Objects.requireNonNull(mitigation, "mitigation must not be null");
        return from(mitigation.mitigation());
    }

    /**
     * Converts a given {@code ActionTerm} object into a {@code RepairActionKey}.
     *
     * @param action the action to convert
     * @return the corresponding {@code RepairActionKey} derived from the input {@code ActionTerm}
     */
    public static RepairActionKey from(@NonNull ActionTerm action) {
        Objects.requireNonNull(action, "action must not be null");
        return new RepairActionKey(action.domain(), action.type(), labelKeys(action.compositeLabels()));
    }

    /**
     * Returns a stable text form of this key.
     *
     * @return the action type, domain, and ordered labels
     */
    public String stableId() {
        String labelId = labels.stream()
                .map(LabelKey::stableId)
                .collect(Collectors.joining(","));
        return actionType.name() + "|" + domain + "|" + labelId;
    }

    private static final Comparator<RepairActionKey> SCALAR_ORDER =
            Comparator.comparing(RepairActionKey::actionType).thenComparing(RepairActionKey::domain);

    /**
     * Compares this {@code RepairActionKey} with the specified {@code RepairActionKey} for order.
     * The comparison is performed using a predefined scalar order, followed by a lexicographical
     * comparison of the associated labels.
     *
     * @param other the key to compare
     * @return a negative integer, zero, or a positive integer as this {@code RepairActionKey}
     * is less than, equal to, or greater than the specified {@code RepairActionKey}
     */
    @Override
    public int compareTo(@NonNull RepairActionKey other) {
        int scalar = SCALAR_ORDER.compare(this, other);
        if (scalar != 0) {
            return scalar;
        }
        int shortestLabelList = Math.min(labels.size(), other.labels.size());
        for (int index = 0; index < shortestLabelList; index++) {
            int labelComparison = labels.get(index).compareTo(other.labels.get(index));
            if (labelComparison != 0) {
                return labelComparison;
            }
        }
        return Integer.compare(labels.size(), other.labels.size());
    }

    /**
     * Converts a list of {@code CompositeLabel} objects into a list of {@code LabelKey} objects.
     *
     * @param labels the list of {@code CompositeLabel} objects to be converted;
     *               may be {@code null}, in which case an empty list is returned
     * @return a list of {@code LabelKey} objects derived from the input {@code CompositeLabel} list;
     * if the input is {@code null}, an empty list is returned
     */
    private static List<LabelKey> labelKeys(List<CompositeLabel> labels) {
        if (labels == null) {
            return List.of();
        }

        return labels.stream()
                .map(LabelKey::from)
                .toList();
    }

    /**
     * The semantic identity of a single label carried by an action.
     *
     * @param category the label category (e.g., node vs. outgoing-data)
     * @param type     the label type name
     * @param value    the label value
     */
    public record LabelKey(@NonNull String category, @NonNull String type, @NonNull String value)
            implements Comparable<LabelKey> {
        /**
         * Validates one semantic label identity.
         *
         * @param category the label category
         * @param type     the label-type name
         * @param value    the label value
         */
        public LabelKey {
            category = Objects.requireNonNull(category, "category must not be null");
            type = Objects.requireNonNull(type, "type must not be null");
            value = Objects.requireNonNull(value, "value must not be null");
        }

        /**
         * Creates a new {@code LabelKey} instance from the given {@code CompositeLabel}.
         *
         * @param label the composite label from which to extract the category, type, and value
         * @return a new {@code LabelKey} instance containing the category, type, and value derived from the provided label
         */
        private static LabelKey from(@NonNull CompositeLabel label) {
            Objects.requireNonNull(label, "label must not be null");
            return new LabelKey(label.category().name(), label.label().type(), label.label().value());
        }

        /**
         * Constructs a stable identifier for the label by concatenating the category, type, and value
         * with a specific format: `category:type=value`.
         *
         * @return a string representing the stable identifier of the label
         */
        private String stableId() {
            return category + ":" + type + "=" + value;
        }

        private static final Comparator<LabelKey> ORDER =
                Comparator.comparing(LabelKey::category).thenComparing(LabelKey::type).thenComparing(LabelKey::value);

        /**
         * Compares this {@code LabelKey} instance with another {@code LabelKey} instance for order.
         * The comparison is based on the category, type, and value fields in that sequence.
         *
         * @param other the key to compare
         * @return a negative integer, zero, or a positive integer as this object is less than,
         * equal to, or greater than the specified {@code LabelKey} instance
         */
        @Override
        public int compareTo(@NonNull LabelKey other) {
            return ORDER.compare(this, other);
        }
    }
}
