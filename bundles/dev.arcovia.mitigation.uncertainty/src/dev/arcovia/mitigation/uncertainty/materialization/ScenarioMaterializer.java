package dev.arcovia.mitigation.uncertainty.materialization;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintyScenario;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.*;
import dev.arcovia.mitigation.ranking.UncertaintySourceMitigationUtils;
import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioSelection;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Materializes a source selection into a concrete DFD. Pipeline stage 3.
 */
public final class ScenarioMaterializer {

    /**
     * Applies the selected alternatives to a base model.
     *
     * @param baseModel  the model to materialize
     * @param selections the selections to apply
     * @return the identified materialized scenario
     * @throws IllegalArgumentException if a selection has an unsupported type
     */
    public MaterializedScenario materialize(
            @NonNull DataFlowDiagramAndDictionary baseModel,
            @NonNull List<ScenarioSelection> selections
    ) {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        Objects.requireNonNull(selections, "selections must not be null");

        var currentModel = baseModel;
        var appliedSelections = new ArrayList<ScenarioSelection>();

        for (var selection : selections) {
            Objects.requireNonNull(selection, "selection must not be null");
            appliedSelections.add(selection);

            if (selection.scenario().isEmpty()) {
                continue;
            }

            currentModel = applyScenario(currentModel, selection.source(), selection.scenario().get());
        }

        var id = appliedSelections.stream()
                .map(this::selectionId)
                .collect(Collectors.joining("__"));

        return new MaterializedScenario(id, currentModel, appliedSelections);
    }

    /**
     * Applies one alternative scenario to the current model.
     *
     * @param current  the model before the alternative is applied
     * @param source   the source that declares the alternative
     * @param scenario the alternative to apply
     * @return the resulting model
     * @throws IllegalArgumentException if the source and scenario types do not match a supported pair
     */
    private DataFlowDiagramAndDictionary applyScenario(
            DataFlowDiagramAndDictionary current,
            UncertaintySource source,
            UncertaintyScenario scenario
    ) {


        if (scenario instanceof DFDExternalUncertaintyScenario externalScenario
            && source instanceof DFDExternalUncertaintySource externalSource) {
            return UncertaintySourceMitigationUtils.chooseExternalScenario(
                    current.dataFlowDiagram(), current.dataDictionary(), externalSource, externalScenario);
        }

        if (scenario instanceof DFDBehaviorUncertaintyScenario behaviorScenario
            && source instanceof DFDBehaviorUncertaintySource behaviorSource) {
            return UncertaintySourceMitigationUtils.chooseBehaviorScenario(
                    current.dataFlowDiagram(), current.dataDictionary(), behaviorSource, behaviorScenario);
        }

        if (scenario instanceof DFDInterfaceUncertaintyScenario interfaceScenario
            && source instanceof DFDInterfaceUncertaintySource interfaceSource) {
            return UncertaintySourceMitigationUtils.chooseInterfaceScenario(
                    current.dataFlowDiagram(), current.dataDictionary(), interfaceSource, interfaceScenario);
        }

        if (scenario instanceof DFDComponentUncertaintyScenario componentScenario
            && source instanceof DFDComponentUncertaintySource componentSource) {
            return UncertaintySourceMitigationUtils.chooseComponentScenario(
                    current.dataFlowDiagram(), current.dataDictionary(), componentSource, componentScenario);
        }

        if (scenario instanceof DFDConnectorUncertaintyScenario connectorScenario
            && source instanceof DFDConnectorUncertaintySource connectorSource) {
            return UncertaintySourceMitigationUtils.chooseConnectorScenario(
                    current.dataFlowDiagram(), current.dataDictionary(), connectorSource, connectorScenario);
        }

        throw new IllegalArgumentException("Unsupported uncertainty scenario type: " + scenario.getClass().getName());
    }

    /**
     * Creates the identifier fragment for one source selection.
     *
     * @param selection the source selection to identify
     * @return the source name and alternative id, or {@code default}
     */
    private String selectionId(ScenarioSelection selection) {
        return selection.scenario()
                .map(uncertaintyScenario -> selection.source().getEntityName() + ":" + uncertaintyScenario.getId())
                .orElseGet(() -> selection.source().getEntityName() + ":default");
    }
}
