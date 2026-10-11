package dev.arcovia.mitigation.uncertainty.verification.solving;

import dev.arcovia.mitigation.ilp.MitigationStrategy;
import dev.arcovia.mitigation.ilp.MitigationType;
import dev.arcovia.mitigation.ilp.RepairActionCostModel;
import dev.arcovia.mitigation.sat.Label;
import dev.arcovia.mitigation.sat.NodeLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairActionCostModelTest {

    @Test
    public void makesAddNodeMoreExpensiveThanDeleteNode() {
        RepairActionCostModel costModel = RepairActionCostModel.standard();

        MitigationStrategy deleteNode = MitigationStrategy.withCostModel(
                List.of(new NodeLabel(new Label("Stereotype", "internal"))),
                MitigationType.DeleteNode,
                costModel);
        MitigationStrategy addNode = MitigationStrategy.withCostModel(
                List.of(new NodeLabel(new Label("Stereotype", "firewall"))),
                MitigationType.AddNode,
                costModel);

        assertEquals(6.0, deleteNode.cost(), 0.0001);
        assertEquals(10.0, addNode.cost(), 0.0001);
        assertTrue(addNode.cost() > deleteNode.cost(),
                "Adding a node must be more expensive than removing a node in the thesis default cost model");
    }

    @Test
    public void supportsCustomRepairCostFunctions() {
        RepairActionCostModel costModel = RepairActionCostModel.builder()
                .withBaseCost(MitigationType.NodeLabel, 2.0)
                .withCostFunction((type, labelCount) -> switch (type) {
                    case NodeLabel -> 2.0 * labelCount;
                    default -> 1.0;
                })
                .build();

        double cost = costModel.costFor(MitigationType.NodeLabel,
                List.of(
                        new NodeLabel(new Label("Location", "EU")),
                        new NodeLabel(new Label("Stereotype", "gateway"))));

        assertEquals(4.0, cost, 0.0001,
                "Custom cost functions must be able to derive costs from the mitigation type and label count");
    }
}
