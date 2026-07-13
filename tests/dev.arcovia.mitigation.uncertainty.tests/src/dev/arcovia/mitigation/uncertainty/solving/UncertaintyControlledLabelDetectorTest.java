package dev.arcovia.mitigation.uncertainty.solving;

import dev.arcovia.mitigation.uncertainty.solving.UncertaintyControlledLabelDetector.ControlledLabelKey;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.datadictionary.Label;
import org.dataflowanalysis.dfd.datadictionary.LabelType;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryFactory;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UncertaintyControlledLabelDetectorTest {

    private static final String NODE_ID = "node-1";
    private static final String PIN_ID = "pin-1";

    @Test
    public void flagsAlternativeInjectedNodeLabelButNotBaseValue() {
        DataFlowDiagramAndDictionary base = model("EU", null);
        DataFlowDiagramAndDictionary scenario = model("nonEU", null);

        Set<ControlledLabelKey> injected = new UncertaintyControlledLabelDetector()
                .detect(base, List.of(scenario));

        assertTrue(injected.contains(new ControlledLabelKey(NODE_ID, "Location", "nonEU")),
                "The scenario-injected node label value must be flagged");
        assertFalse(injected.contains(new ControlledLabelKey(NODE_ID, "Location", "EU")),
                "The base-model node label value must not be flagged (value precision)");
    }

    @Test
    public void flagsAlternativeInjectedOutgoingDataLabel() {
        DataFlowDiagramAndDictionary base = model("EU", null);
        DataFlowDiagramAndDictionary scenario = model("EU", "Encrypted");

        Set<ControlledLabelKey> injected = new UncertaintyControlledLabelDetector()
                .detect(base, List.of(scenario));

        assertEquals(Set.of(new ControlledLabelKey(PIN_ID, "Encryption", "Encrypted")), injected,
                "Exactly the scenario-injected outgoing data label must be flagged");
    }

    @Test
    public void identicalScenarioYieldsNoControlledLabels() {
        DataFlowDiagramAndDictionary base = model("EU", "Encrypted");
        DataFlowDiagramAndDictionary scenario = model("EU", "Encrypted");

        Set<ControlledLabelKey> injected = new UncertaintyControlledLabelDetector()
                .detect(base, List.of(scenario));

        assertTrue(injected.isEmpty(), "A scenario identical to the base injects nothing");
    }

    private DataFlowDiagramAndDictionary model(String nodeLabelValue, String outgoingLabelValue) {
        var ddFactory = datadictionaryFactory.eINSTANCE;
        var dfdFactory = dataflowdiagramFactory.eINSTANCE;

        DataDictionary dictionary = ddFactory.createDataDictionary();
        Label locationLabel = label(dictionary, "Location", nodeLabelValue);

        DataFlowDiagram diagram = dfdFactory.createDataFlowDiagram();
        var node = dfdFactory.createProcess();
        node.setId(NODE_ID);
        node.setEntityName("node");
        node.getProperties().add(locationLabel);
        diagram.getNodes().add(node);

        var behavior = ddFactory.createBehavior();
        var outPin = ddFactory.createPin();
        outPin.setId(PIN_ID);
        behavior.getOutPin().add(outPin);
        var assignment = ddFactory.createSetAssignment();
        assignment.setOutputPin(outPin);
        if (outgoingLabelValue != null) {
            assignment.getOutputLabels().add(label(dictionary, "Encryption", outgoingLabelValue));
        }
        behavior.getAssignment().add(assignment);
        dictionary.getBehavior().add(behavior);
        node.setBehavior(behavior);

        return new DataFlowDiagramAndDictionary(diagram, dictionary);
    }

    private Label label(DataDictionary dictionary, String typeName, String valueName) {
        var ddFactory = datadictionaryFactory.eINSTANCE;
        LabelType labelType = dictionary.getLabelTypes().stream()
                .filter(type -> type.getEntityName().equals(typeName))
                .findFirst()
                .orElseGet(() -> {
                    LabelType created = ddFactory.createLabelType();
                    created.setEntityName(typeName);
                    dictionary.getLabelTypes().add(created);
                    return created;
                });
        Label label = ddFactory.createLabel();
        label.setEntityName(valueName);
        labelType.getLabel().add(label);
        return label;
    }
}
