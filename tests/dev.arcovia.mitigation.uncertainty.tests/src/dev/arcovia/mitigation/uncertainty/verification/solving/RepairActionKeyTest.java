package dev.arcovia.mitigation.uncertainty.verification.solving;

import dev.arcovia.mitigation.cost.ActionType;
import dev.arcovia.mitigation.cost.RepairActionKey;
import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.RepairActionKeys;
import dev.arcovia.mitigation.sat.Label;
import dev.arcovia.mitigation.sat.NodeLabel;
import dev.arcovia.mitigation.sat.OutgoingDataLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RepairActionKeyTest {

    @Test
    public void usesActionDomainTypeAndLabelSemantics() {
        RepairActionKey first = RepairActionKeys.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));
        RepairActionKey equivalent = RepairActionKeys.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertEquals(first.stableId(), equivalent.stableId());
    }

    @Test
    public void distinguishesNodeLabelsFromOutgoingDataLabels() {
        RepairActionKey nodeLabelAction = RepairActionKeys.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));
        RepairActionKey dataLabelAction = RepairActionKeys.from(new ActionTerm(
                "database",
                List.of(new OutgoingDataLabel(new Label("Location", "EU"))),
                ActionType.Adding));

        assertNotEquals(nodeLabelAction, dataLabelAction);
    }

    @Test
    public void preservesLabelOrderBecauseStructuralActionsUseTheFirstLabelAsElementName() {
        RepairActionKey firstOrder = RepairActionKeys.from(new ActionTerm(
                "flow",
                List.of(
                        new NodeLabel(new Label("Stereotype", "Firewall")),
                        new OutgoingDataLabel(new Label("Sensitivity", "Personal"))),
                ActionType.AddNode));
        RepairActionKey secondOrder = RepairActionKeys.from(new ActionTerm(
                "flow",
                List.of(
                        new OutgoingDataLabel(new Label("Sensitivity", "Personal")),
                        new NodeLabel(new Label("Stereotype", "Firewall"))),
                ActionType.AddNode));

        assertNotEquals(firstOrder, secondOrder);
    }
}
