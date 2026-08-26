package dev.arcovia.mitigation.uncertainty.pruning;

import dev.abunai.confidentiality.analysis.dfd.DFDQueryHelper;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDUncertaintySource;
import org.apache.log4j.Logger;
import org.dataflowanalysis.analysis.core.AbstractVertex;
import org.dataflowanalysis.analysis.dfd.DFDDataFlowAnalysisBuilder;
import org.dataflowanalysis.analysis.dfd.resource.DFDModelResourceProvider;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Removes sources that cannot influence any base-model transpose flow graph.
 */
public final class SourceImpactFilter {

    private static final Logger LOGGER = Logger.getLogger(SourceImpactFilter.class);

    /**
     * Retains sources whose target appears in a base-model flow graph.
     *
     * @param baseModel       the unmaterialized model
     * @param selectedSources the sources to filter
     * @return the retained sources in input order
     */
    public List<UncertaintySource> retainImpactful(
            @NonNull DataFlowDiagramAndDictionary baseModel,
            @NonNull List<UncertaintySource> selectedSources) {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        Objects.requireNonNull(selectedSources, "selectedSources must not be null");
        if (selectedSources.isEmpty()) {
            return List.of();
        }
        if (TopologyEffect.anyChangesTopology(selectedSources)) {
            LOGGER.info("impact pruning disabled: " + selectedSources.size()
                        + " selected sources include at least one that rewires the diagram, so a node "
                        + "that lies on no base-model transpose flow graph may become reachable in an "
                        + "alternative scenario. All sources are retained.");
            return List.copyOf(selectedSources);
        }

        DFDQueryHelper queryHelper = new DFDQueryHelper(baseModelVertices(baseModel));
        List<UncertaintySource> impactful = selectedSources.stream()
                .filter(source -> queryHelper.hasTargetNode((DFDUncertaintySource) source))
                .toList();

        if (impactful.size() != selectedSources.size()) {
            LOGGER.info("A1 impact pruning: retained " + impactful.size() + " of " + selectedSources.size()
                        + " selected uncertainty sources (dropped sources whose target node is on no transpose flow graph).");
        }
        return impactful;
    }

    /**
     * Runs one base-model analysis pass and collects every transpose-flow-graph vertex.
     *
     * @param baseModel the model to analyze
     * @return all vertices that participate in a base-model transpose flow graph
     */
    private List<? extends AbstractVertex<?>> baseModelVertices(DataFlowDiagramAndDictionary baseModel) {
        var resourceProvider = new DFDModelResourceProvider(baseModel.dataDictionary(), baseModel.dataFlowDiagram());
        var analysis = new DFDDataFlowAnalysisBuilder().standalone().useCustomResourceProvider(resourceProvider).build();
        analysis.initializeAnalysis();
        var flowGraphs = analysis.findFlowGraphs();
        flowGraphs.evaluate();
        return flowGraphs.getTransposeFlowGraphs().stream()
                .flatMap(transposeFlowGraph -> transposeFlowGraph.getVertices().stream())
                .toList();
    }
}
