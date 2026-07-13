package dev.arcovia.mitigation.uncertainty.loading;

import org.eclipse.jdt.annotation.NonNull;

import java.nio.file.Path;

/**
 * Immutable triple of on-disk resource paths that specify one uncertainty-annotated model.
 *
 * @param dataFlowDiagramPath  path to the {@code .dataflowdiagram} resource
 * @param dataDictionaryPath   path to the {@code .datadictionary} resource
 * @param uncertaintyModelPath path to the ABUNAI {@code .uncertainty} resource
 */
public record UncertaintyModelSpec(
        @NonNull Path dataFlowDiagramPath,
        @NonNull Path dataDictionaryPath,
        @NonNull Path uncertaintyModelPath
) {
}
