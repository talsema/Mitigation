package dev.arcovia.mitigation.uncertainty.solving;

import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.ActionType;
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
        RepairActionKey first = RepairActionKey.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));
        RepairActionKey equivalent = RepairActionKey.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));

        assertEquals(first, equivalent);
        assertEquals(first.hashCode(), equivalent.hashCode());
        assertEquals(first.stableId(), equivalent.stableId());
    }

    @Test
    public void distinguishesNodeLabelsFromOutgoingDataLabels() {
        RepairActionKey nodeLabelAction = RepairActionKey.from(new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "EU"))),
                ActionType.Adding));
        RepairActionKey dataLabelAction = RepairActionKey.from(new ActionTerm(
                "database",
                List.of(new OutgoingDataLabel(new Label("Location", "EU"))),
                ActionType.Adding));

        assertNotEquals(nodeLabelAction, dataLabelAction);
    }

    @Test
    public void preservesLabelOrderBecauseStructuralActionsUseTheFirstLabelAsElementName() {
        RepairActionKey firstOrder = RepairActionKey.from(new ActionTerm(
                "flow",
                List.of(
                        new NodeLabel(new Label("Stereotype", "Firewall")),
                        new OutgoingDataLabel(new Label("Sensitivity", "Personal"))),
                ActionType.AddNode));
        RepairActionKey secondOrder = RepairActionKey.from(new ActionTerm(
                "flow",
                List.of(
                        new OutgoingDataLabel(new Label("Sensitivity", "Personal")),
                        new NodeLabel(new Label("Stereotype", "Firewall"))),
                ActionType.AddNode));

        assertNotEquals(firstOrder, secondOrder);
    }
}
