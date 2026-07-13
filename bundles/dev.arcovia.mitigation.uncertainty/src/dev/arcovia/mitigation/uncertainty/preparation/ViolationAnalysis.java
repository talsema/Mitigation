package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.Node;
import org.dataflowanalysis.analysis.dfd.DFDDataFlowAnalysisBuilder;
import org.dataflowanalysis.analysis.dfd.resource.DFDModelResourceProvider;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds constraint violations and rejects cyclic data-flow diagrams.
 */
public final class ViolationAnalysis {

    /**
     * Finds violating nodes after rejecting cyclic models.
     *
     * @param dfd         the model to analyze
     * @param constraints the constraints to evaluate
     * @return the violating nodes
     * @throws IllegalStateException if the model contains a data-flow cycle
     */
    public Set<Node> findViolatingNodes(@NonNull DataFlowDiagramAndDictionary dfd,
                                        @NonNull List<Constraint> constraints) {
        Objects.requireNonNull(dfd, "dfd must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        var resourceProvider = new DFDModelResourceProvider(dfd.dataDictionary(), dfd.dataFlowDiagram());
        var analysis = new DFDDataFlowAnalysisBuilder().standalone().useCustomResourceProvider(resourceProvider).build();

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();
        if (flowGraph.wasCyclic()) {
            throw new IllegalStateException(
                    "DFD model contains a cyclic data flow; transpose flow graph extraction and therefore "
                    + "the confidentiality analysis are unreliable on cyclic models.");
        }

        return constraints.stream()
                .flatMap(constraint -> constraint.determineViolations(flowGraph).stream())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
