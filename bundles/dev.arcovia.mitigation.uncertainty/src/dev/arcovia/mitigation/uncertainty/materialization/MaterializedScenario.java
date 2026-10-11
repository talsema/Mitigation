package dev.arcovia.mitigation.uncertainty.materialization;

import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioSelection;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * One materialized scenario: a concrete DFD produced for a specific scenario combination.
 *
 * @param id         deterministic identifier derived from the applied selections
 * @param model      the materialized DFD; default-only selections retain the read-only base model
 *                   and alternative selections use an independent copy
 * @param selections the source choices that produced this scenario
 */
public record MaterializedScenario(
        @NonNull String id,
        @NonNull DataFlowDiagramAndDictionary model,
        @NonNull List<ScenarioSelection> selections
) {

    /**
     * Validates the materialized model and snapshots the selections that produced it.
     *
     * @param id         the deterministic scenario identifier
     * @param model      the materialized model
     * @param selections the source choices used to create the model
     */
    public MaterializedScenario {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(model, "model must not be null");
        selections = List.copyOf(Objects.requireNonNull(selections, "selections must not be null"));
    }
}
