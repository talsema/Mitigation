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
 * Finds constraint violations, and by default rejects cyclic data-flow diagrams.
 * <p>
 * Transpose flow graph extraction unrolls cycles heuristically, so a reading taken on a cyclic
 * model can differ from what the model means. The default therefore refuses such models. Repeated
 * analyses of the same cyclic model were measured and agree with each other, so a caller that
 * accepts the heuristic and reports its results separately may opt in through
 * {@link #ViolationAnalysis(boolean)}. Reproducibility is not correctness, and an opted-in reading
 * carries the same caveat as the heuristic that produced it.
 */
public final class ViolationAnalysis {

    private final boolean allowCyclic;

    /** Creates an analysis that refuses cyclic models. */
    public ViolationAnalysis() {
        this(false);
    }

    /**
     * @param allowCyclic whether to analyse a cyclic model instead of rejecting it; results
     *                    obtained this way must be reported as a separate stratum
     */
    public ViolationAnalysis(boolean allowCyclic) {
        this.allowCyclic = allowCyclic;
    }

    /**
     * One reading of one model.
     *
     * @param violatingNodes the nodes violating at least one constraint
     * @param cyclic         whether extraction reported unrolling a cycle
     */
    public record Reading(@NonNull Set<Node> violatingNodes, boolean cyclic) {

        /** Validates and defensively copies the reading. */
        public Reading {
            violatingNodes = Set.copyOf(Objects.requireNonNull(violatingNodes, "violatingNodes must not be null"));
        }
    }

    /**
     * Finds violating nodes and reports whether the analysed model was cyclic.
     *
     * @param dfd         the model to analyze
     * @param constraints the constraints to evaluate
     * @return the violating nodes and the cyclicity of the analysed model
     * @throws IllegalStateException if the model contains a data-flow cycle and this analysis was
     *                               not created with cyclic models allowed
     */
    public Reading analyze(@NonNull DataFlowDiagramAndDictionary dfd,
                           @NonNull List<Constraint> constraints) {
        Objects.requireNonNull(dfd, "dfd must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        var resourceProvider = new DFDModelResourceProvider(dfd.dataDictionary(), dfd.dataFlowDiagram());
        var analysis = new DFDDataFlowAnalysisBuilder().standalone().useCustomResourceProvider(resourceProvider).build();

        analysis.initializeAnalysis();
        var flowGraph = analysis.findFlowGraphs();
        flowGraph.evaluate();
        boolean cyclic = flowGraph.wasCyclic();
        if (cyclic && !allowCyclic) {
            throw new IllegalStateException(
                    "DFD model contains a cyclic data flow; transpose flow graph extraction and therefore "
                    + "the confidentiality analysis are unreliable on cyclic models.");
        }

        Set<Node> violating = constraints.stream()
                .flatMap(constraint -> constraint.determineViolations(flowGraph).stream())
                .collect(Collectors.toCollection(HashSet::new));
        return new Reading(violating, cyclic);
    }

    /**
     * Finds violating nodes, discarding the cyclicity of the analysed model.
     *
     * @param dfd         the model to analyze
     * @param constraints the constraints to evaluate
     * @return the violating nodes
     * @throws IllegalStateException if the model contains a data-flow cycle and this analysis was
     *                               not created with cyclic models allowed
     */
    public Set<Node> findViolatingNodes(@NonNull DataFlowDiagramAndDictionary dfd,
                                        @NonNull List<Constraint> constraints) {
        return analyze(dfd, constraints).violatingNodes();
    }
}
