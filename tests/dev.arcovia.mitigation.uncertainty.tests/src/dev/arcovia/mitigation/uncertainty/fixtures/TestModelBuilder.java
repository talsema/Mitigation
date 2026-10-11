package dev.arcovia.mitigation.uncertainty.fixtures;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.*;
import org.dataflowanalysis.dfd.dataflowdiagram.*;
import org.dataflowanalysis.dfd.dataflowdiagram.Process;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper to assemble small data flow diagrams in code.
 */
public final class TestModelBuilder {

    private final DataFlowDiagram diagram = dataflowdiagramFactory.eINSTANCE.createDataFlowDiagram();
    private final DataDictionary dictionary = datadictionaryFactory.eINSTANCE.createDataDictionary();
    private final Map<String, Label> labels = new HashMap<>();
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, Pin> outPins = new HashMap<>();
    private final Map<String, Pin> inPins = new HashMap<>();

    /**
     * Declares a label type and its values, for example {@code Location} with {@code EU}.
     *
     * @param type   the label type name
     * @param values the label values
     * @return this builder
     */
    public TestModelBuilder labelType(String type, String... values) {
        LabelType labelType = datadictionaryFactory.eINSTANCE.createLabelType();
        labelType.setId("lt" + type);
        labelType.setEntityName(type);
        Arrays.stream(values).forEach(value -> {
            Label label = datadictionaryFactory.eINSTANCE.createLabel();
            label.setId("l" + value);
            label.setEntityName(value);
            labelType.getLabel().add(label);
            labels.put(value, label);
        });
        dictionary.getLabelTypes().add(labelType);
        return this;
    }

    /**
     * Adds an external source that emits data carrying the given labels.
     *
     * @param name         the node name, also its identifier
     * @param outputLabels labels assigned to the emitted data
     * @return this builder
     */
    public TestModelBuilder source(String name, String... outputLabels) {
        Behavior behavior = behavior(name);
        Pin out = pin("po" + name, name + "_out");
        behavior.getOutPin().add(out);
        outPins.put(name, out);

        Assignment assignment = datadictionaryFactory.eINSTANCE.createAssignment();
        assignment.setId("a" + name);
        assignment.setEntityName(name);
        assignment.setOutputPin(out);
        Arrays.stream(outputLabels).forEach(label -> assignment.getOutputLabels().add(label(label)));
        assignment.setTerm(datadictionaryFactory.eINSTANCE.createTRUE());
        behavior.getAssignment().add(assignment);

        External node = dataflowdiagramFactory.eINSTANCE.createExternal();
        register(node, name, behavior);
        return this;
    }

    /**
     * Adds a process that forwards everything on its input to its output.
     *
     * @param name the node name, also its identifier
     * @return this builder
     */
    public TestModelBuilder forwarder(String name) {
        Behavior behavior = behavior(name);
        Pin in = pin("pi" + name, name + "_in");
        Pin out = pin("po" + name, name + "_out");
        behavior.getInPin().add(in);
        behavior.getOutPin().add(out);
        inPins.put(name, in);
        outPins.put(name, out);

        ForwardingAssignment forwarding = datadictionaryFactory.eINSTANCE.createForwardingAssignment();
        forwarding.setId("fwd" + name);
        forwarding.getInputPins().add(in);
        forwarding.setOutputPin(out);
        behavior.getAssignment().add(forwarding);

        Process node = dataflowdiagramFactory.eINSTANCE.createProcess();
        register(node, name, behavior);
        return this;
    }

    /**
     * Adds a terminal store carrying the given node labels.
     *
     * @param name       the node name, also its identifier
     * @param nodeLabels labels describing the store, for example its location
     * @return this builder
     */
    public TestModelBuilder store(String name, String... nodeLabels) {
        Behavior behavior = behavior(name);
        Pin in = pin("pi" + name, name + "_in");
        behavior.getInPin().add(in);
        inPins.put(name, in);

        Store node = dataflowdiagramFactory.eINSTANCE.createStore();
        register(node, name, behavior);
        for (String label : nodeLabels) {
            node.getProperties().add(label(label));
        }
        return this;
    }

    /**
     * Attaches node properties to a node that already exists, for example its location.
     *
     * @param name       the node name
     * @param nodeLabels the labels describing the node
     * @return this builder
     */
    public TestModelBuilder nodeLabels(String name, String... nodeLabels) {
        Node node = nodeByName(name);
        Arrays.stream(nodeLabels).forEach(value -> node.getProperties().add(label(value)));
        return this;
    }

    /**
     * Connects the output of one node to the input of another.
     *
     * @param id   the flow identifier, used by uncertainty sources that reroute it
     * @param from the source node name
     * @param to   the destination node name
     * @return this builder
     */
    public TestModelBuilder flow(String id, String from, String to) {
        Flow flow = dataflowdiagramFactory.eINSTANCE.createFlow();
        flow.setId(id);
        flow.setEntityName(id);
        flow.setSourceNode(nodes.get(from));
        flow.setDestinationNode(nodes.get(to));
        flow.setSourcePin(outPins.get(from));
        flow.setDestinationPin(inPins.get(to));
        diagram.getFlows().add(flow);
        return this;
    }

    /**
     * Returns the assembled model without uncertainty.
     *
     * @return the diagram and its dictionary
     */
    public DataFlowDiagramAndDictionary build() {
        return new DataFlowDiagramAndDictionary(diagram, dictionary);
    }

    /**
     * Returns the assembled model with the given uncertainty sources attached.
     *
     * @param sources the declared uncertainty sources
     * @return the loaded model the repair pipeline consumes
     */
    public LoadedUncertaintyModel build(List<UncertaintySource> sources) {
        return new LoadedUncertaintyModel(build(), List.copyOf(sources));
    }

    /**
     * Looks up a flow by the identifier given to {@link #flow}.
     *
     * @param id the flow identifier
     * @return the flow, for use as an uncertainty source target
     */
    public Flow flowById(String id) {
        return diagram.getFlows().stream()
                .filter(flow -> flow.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no flow " + id));
    }

    /**
     * Looks up a node by the name given when it was added.
     *
     * @param name the node name
     * @return the node
     */
    public Node nodeByName(String name) {
        Node node = nodes.get(name);
        if (node == null) {
            throw new IllegalArgumentException("no node " + name);
        }
        return node;
    }

    /**
     * Returns the input pin of a node, which a rerouting scenario needs as its new destination.
     *
     * @param name the node name
     * @return the node's input pin
     */
    public Pin inPinOf(String name) {
        Pin pin = inPins.get(name);
        if (pin == null) {
            throw new IllegalArgumentException("no input pin on " + name);
        }
        return pin;
    }

    /**
     * Returns a declared label, which an external uncertainty source needs as the property it
     * replaces or installs.
     *
     * @param value the label value, for example {@code EU}
     * @return the label instance used by this model
     */
    public Label labelByName(String value) {
        return label(value);
    }

    private Behavior behavior(String name) {
        Behavior behavior = datadictionaryFactory.eINSTANCE.createBehavior();
        behavior.setId("b" + name);
        behavior.setEntityName(name);
        dictionary.getBehavior().add(behavior);
        return behavior;
    }

    private static Pin pin(String id, String name) {
        Pin pin = datadictionaryFactory.eINSTANCE.createPin();
        pin.setId(id);
        pin.setEntityName(name);
        return pin;
    }

    private Label label(String value) {
        Label label = labels.get(value);
        if (label == null) {
            throw new IllegalArgumentException("undeclared label " + value + "; call labelType first");
        }
        return label;
    }

    private void register(Node node, String name, Behavior behavior) {
        node.setId("n" + name);
        node.setEntityName(name);
        node.setBehavior(behavior);
        diagram.getNodes().add(node);
        nodes.put(name, node);
    }
}
