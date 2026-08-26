package dev.arcovia.mitigation.uncertainty.pruning;

import dev.abunai.confidentiality.analysis.dfd.DFDQueryHelper;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDUncertaintySource;
import org.apache.log4j.Logger;
import org.dataflowanalysis.analysis.dfd.DFDDataFlowAnalysisBuilder;
import org.dataflowanalysis.analysis.dfd.resource.DFDModelResourceProvider;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.*;

/**
 * Groups sources that share a base-model transpose flow graph.
 */
public final class SourcePartitioner {

    private static final Logger LOGGER = Logger.getLogger(SourcePartitioner.class);

    /**
     * Partitions sources by shared base-model flow graphs.
     *
     * @param baseModel the unmaterialized model
     * @param sources   the impact-filtered sources
     * @return ordered source clusters, or an empty list
     */
    public List<List<UncertaintySource>> partition(
            @NonNull DataFlowDiagramAndDictionary baseModel,
            @NonNull List<UncertaintySource> sources
    ) {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        if (sources.isEmpty()) {
            return List.of();
        }
        if (TopologyEffect.anyChangesTopology(sources)) {
            LOGGER.info("independence decomposition disabled: at least one of the " + sources.size()
                        + " sources rewires the diagram, so two sources that share no base-model "
                        + "transpose flow graph may still interact after materialization. "
                        + "Solving over the undecomposed space.");
            return List.of(List.copyOf(sources));
        }

        int[] parent = newUnionFind(sources.size());
        for (DFDQueryHelper queryHelper : baseModelTransposeFlowGraphs(baseModel)) {
            int firstOnThisGraph = -1;
            for (int i = 0; i < sources.size(); i++) {
                if (queryHelper.hasTargetNode((DFDUncertaintySource) sources.get(i))) {
                    if (firstOnThisGraph == -1) {
                        firstOnThisGraph = i;
                    } else {
                        union(parent, firstOnThisGraph, i);
                    }
                }
            }
        }

        // Group by union-find root, preserving first-appearance order of both clusters and members.
        Map<Integer, List<UncertaintySource>> clustersByRoot = new LinkedHashMap<>();
        for (int i = 0; i < sources.size(); i++) {
            clustersByRoot.computeIfAbsent(find(parent, i), key -> new ArrayList<>()).add(sources.get(i));
        }
        return List.copyOf(clustersByRoot.values());
    }

    /**
     * Runs one base-model analysis pass and creates one query helper per transpose flow graph.
     *
     * @param baseModel the model whose transpose flow graphs are inspected
     * @return helpers exposing the vertices of each discovered transpose flow graph
     */
    private List<DFDQueryHelper> baseModelTransposeFlowGraphs(DataFlowDiagramAndDictionary baseModel) {
        var resourceProvider = new DFDModelResourceProvider(baseModel.dataDictionary(), baseModel.dataFlowDiagram());
        var analysis = new DFDDataFlowAnalysisBuilder().standalone().useCustomResourceProvider(resourceProvider).build();
        analysis.initializeAnalysis();
        var flowGraphs = analysis.findFlowGraphs();
        flowGraphs.evaluate();
        return flowGraphs.getTransposeFlowGraphs().stream()
                .map(transposeFlowGraph -> new DFDQueryHelper(transposeFlowGraph.getVertices()))
                .toList();
    }

    /**
     * Creates a union-find parent array whose elements initially form singleton sets.
     *
     * @param size the number of source positions to track
     * @return a parent array in which each index is its own root
     */
    private static int[] newUnionFind(int size) {
        int[] parent = new int[size];
        for (int i = 0; i < size; i++) {
            parent[i] = i;
        }
        return parent;
    }

    /**
     * Finds a node's union-find root and compresses the traversed path.
     *
     * @param parent the union-find parent array
     * @param node   the source position whose cluster root is requested
     * @return the root position of {@code node}'s cluster
     */
    private static int find(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    /**
     * Merges the clusters containing two source positions while preserving first-occurrence order.
     *
     * @param parent the union-find parent array to update
     * @param a      the first source position
     * @param b      the second source position
     */
    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            // Attach the later root to the earlier one so cluster order follows first appearance.
            parent[Math.max(rootA, rootB)] = Math.min(rootA, rootB);
        }
    }
}
