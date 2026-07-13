package dev.arcovia.mitigation.uncertainty.solving;

import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.*;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds label values that alternatives inject, but the base model does not contain.
 */
public final class UncertaintyControlledLabelDetector {

    /**
     * Identifies a concrete label value that uncertainty materialization controls. For node
     * labels, {@code elementId} is the node id; for outgoing data labels it is the id of
     * the assignment's output pin (which is also the domain of the corresponding
     * outgoing-data repair actions).
     */
    public record ControlledLabelKey(@NonNull String elementId, @NonNull String labelType,
                                     @NonNull String labelValue) {
        /**
         * Validates one uncertainty-controlled label identity.
         *
         * @param elementId  the node or output-pin identifier
         * @param labelType  the label-type name
         * @param labelValue the concrete label value
         */
        public ControlledLabelKey {
            elementId = Objects.requireNonNull(elementId, "elementId must not be null");
            labelType = Objects.requireNonNull(labelType, "labelType must not be null");
            labelValue = Objects.requireNonNull(labelValue, "labelValue must not be null");
        }
    }

    /**
     * @param baseModel      the unmaterialized base model (default-everything configuration)
     * @param scenarioModels the materialized scenario models (including the default copy)
     * @return the set of node and outgoing-data label values injected by some alternative
     * scenario relative to the base model
     */
    public Set<ControlledLabelKey> detect(@NonNull DataFlowDiagramAndDictionary baseModel,
                                          @NonNull List<DataFlowDiagramAndDictionary> scenarioModels) {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        Objects.requireNonNull(scenarioModels, "scenarioModels must not be null");

        Set<ControlledLabelKey> baseLabels = allLabels(baseModel).collect(Collectors.toSet());
        return scenarioModels.stream()
                .flatMap(this::allLabels)
                .filter(scenarioLabel -> !baseLabels.contains(scenarioLabel))
                .collect(Collectors.toSet());
    }

    /**
     * Combines the node labels and outgoing data labels extracted from the given data flow
     * diagram and dictionary model to produce a unified stream of controlled label keys.
     *
     * @param model The data flow diagram and dictionary model from which labels are derived.
     * @return A stream of {@code ControlledLabelKey} objects representing both the labels
     * associated with nodes and the outgoing data labels in the provided model.
     */
    private Stream<ControlledLabelKey> allLabels(DataFlowDiagramAndDictionary model) {
        return Stream.concat(nodeLabels(model), outgoingDataLabels(model));
    }

    /**
     * Extracts the node labels for all nodes in a given data flow diagram and dictionary model.
     * The labels are represented as {@code ControlledLabelKey} objects, which include the node
     * ID and label characteristics.
     *
     * @param model The data flow diagram and dictionary model from which to extract node labels.
     * @return A stream of {@code ControlledLabelKey} objects representing the labels associated
     * with the nodes in the provided model.
     */
    private Stream<ControlledLabelKey> nodeLabels(DataFlowDiagramAndDictionary model) {
        return model.dataFlowDiagram().getNodes().stream()
                .flatMap(node -> node.getProperties().stream()
                        .map(label -> key(node.getId(), label)));
    }

    /**
     * Streams labels assigned to outgoing data.
     *
     * @param model the model to inspect
     * @return the labels keyed by output pin
     */
    private Stream<ControlledLabelKey> outgoingDataLabels(DataFlowDiagramAndDictionary model) {
        return model.dataDictionary().getBehavior().stream()
                .flatMap(behavior -> behavior.getAssignment().stream())
                .filter(assignment -> assignment.getOutputPin() != null)
                .flatMap(assignment -> outputLabels(assignment).stream()
                        .map(label -> key(assignment.getOutputPin().getId(), label)));
    }

    /**
     * Returns labels written by an assignment.
     *
     * @param assignment the assignment to inspect
     * @return its output labels, or an empty list
     */
    private List<Label> outputLabels(AbstractAssignment assignment) {
        if (assignment instanceof Assignment cast) {
            return cast.getOutputLabels();
        }
        if (assignment instanceof SetAssignment cast) {
            return cast.getOutputLabels();
        }
        return List.of();
    }

    /**
     * Builds a key for one labeled model element.
     *
     * @param elementId the node or output-pin id
     * @param label     the label value
     * @return the corresponding controlled-label key
     */
    private ControlledLabelKey key(String elementId, Label label) {
        String type = ((LabelType) label.eContainer()).getEntityName();
        return new ControlledLabelKey(elementId, type, label.getEntityName());
    }
}
