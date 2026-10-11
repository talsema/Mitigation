package dev.arcovia.mitigation.ilp;

import dev.arcovia.mitigation.cost.RepairActionKey;
import dev.arcovia.mitigation.sat.CompositeLabel;

import java.util.List;
import java.util.Objects;

/**
 * Converts ILP repair actions to cost-model identities.
 */
public final class RepairActionKeys {

    /**
     * Prevents utility-class instantiation.
     */
    private RepairActionKeys() {
    }

    /**
     * Returns the cost-model key for a mitigation.
     *
     * @param mitigation the mitigation to identify
     * @return the semantic repair-action key
     */
    public static RepairActionKey from(Mitigation mitigation) {
        return from(Objects.requireNonNull(mitigation, "mitigation must not be null").mitigation());
    }

    /**
     * Returns the cost-model key for an ILP action.
     *
     * @param action the action to identify
     * @return the semantic repair-action key
     */
    public static RepairActionKey from(ActionTerm action) {
        Objects.requireNonNull(action, "action must not be null");
        List<RepairActionKey.LabelKey> labels = action.compositeLabels() == null
                ? List.of()
                : action.compositeLabels().stream().map(RepairActionKeys::labelKey).toList();
        return new RepairActionKey(action.domain(), action.type(), labels);
    }

    /**
     * Converts one ILP label into a cost-model label key.
     *
     * @param label the label to identify
     * @return the semantic label key
     */
    private static RepairActionKey.LabelKey labelKey(CompositeLabel label) {
        Objects.requireNonNull(label, "label must not be null");
        return new RepairActionKey.LabelKey(label.category().name(), label.label().type(), label.label().value());
    }
}
