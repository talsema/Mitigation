package dev.arcovia.mitigation.uncertainty.loading;

import dev.abunai.confidentiality.analysis.core.UncertaintySourceManager;
import dev.abunai.confidentiality.analysis.core.UncertaintySourceType;
import dev.abunai.confidentiality.analysis.dfd.DFDUncertaintyResourceProvider;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import org.apache.log4j.Logger;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.emf.common.util.URI;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Loads one uncertainty-annotated model. Pipeline stage 1.
 */
public final class UncertaintyModelLoader {

    private static final Logger LOGGER = Logger.getLogger(UncertaintyModelLoader.class);

    /**
     * Loads and validates the resources described by {@code spec}.
     *
     * @param spec the three model-resource paths
     * @return the loaded base model and its declared sources
     */
    public LoadedUncertaintyModel load(@NonNull UncertaintyModelSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        var provider = new DFDUncertaintyResourceProvider(
                URI.createFileURI(spec.dataFlowDiagramPath().toAbsolutePath().normalize().toString()),
                URI.createFileURI(spec.dataDictionaryPath().toAbsolutePath().normalize().toString()),
                URI.createFileURI(spec.uncertaintyModelPath().toAbsolutePath().normalize().toString()));
        provider.setupResources();
        provider.loadRequiredResources();

        var sourceManager = new UncertaintySourceManager(
                provider.getUncertaintySourceCollection(),
                UncertaintySourceType.DFD);

        var sources = sourceManager.getUncertaintySources();

        return new LoadedUncertaintyModel(
                new DataFlowDiagramAndDictionary(provider.getDataFlowDiagram(), provider.getDataDictionary()),
                sources
        );
    }
}
