package dev.arcovia.mitigation.uncertainty.loading;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * In-memory result of loading an uncertainty model
 *
 * @param baseModel the loaded base model, never mutated by the repair
 * @param sources   the uncertainty sources declared in the loaded model
 */
public record LoadedUncertaintyModel(
        @NonNull DataFlowDiagramAndDictionary baseModel,
        @NonNull List<UncertaintySource> sources
) {

    /**
     * Validates the loaded model and takes an immutable snapshot of its declared sources.
     *
     * @param baseModel the base diagram and data dictionary
     * @param sources   the declared uncertainty sources
     */
    public LoadedUncertaintyModel {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources must not be null"));
    }
}
