package dev.arcovia.mitigation.uncertainty.preparation;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.ilp.Node;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Prepares one materialized scenario for robust solving. Pipeline stage 4.
 */
public final class RepairPreparationService {

    private final ConstraintPreparation constraintPreparation = new ConstraintPreparation();
    private final ViolationAnalysis violationAnalysis;
    private final ContradictionDerivation contradictionDerivation = new ContradictionDerivation();

    /** Creates a service that refuses cyclic models. */
    public RepairPreparationService() {
        this(false);
    }

    /**
     * @param allowCyclic whether to prepare cyclic models instead of rejecting them; results
     *                    obtained this way must be reported as a separate stratum
     */
    public RepairPreparationService(boolean allowCyclic) {
        this.violationAnalysis = new ViolationAnalysis(allowCyclic);
    }

    /**
     * Derives violations, repair alternatives, and conflicts for one model.
     *
     * @param dfd         the model to analyze
     * @param constraints the constraints to prepare
     * @return the solver-ready preparation result
     */
    public RepairPreparationResult prepare(@NonNull DataFlowDiagramAndDictionary dfd,
                                           @NonNull List<Constraint> constraints) {
        Objects.requireNonNull(dfd, "dfd must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        List<Constraint> preparedConstraints = constraintPreparation.prepare(constraints);
        ViolationAnalysis.Reading reading = violationAnalysis.analyze(dfd, preparedConstraints);
        Set<Node> violatingNodes = reading.violatingNodes();

        CoverageDerivation coverageDerivation = new CoverageDerivation();
        violatingNodes.forEach(coverageDerivation::deriveFor);

        List<List<Mitigation>> contradictions =
                contradictionDerivation.derive(coverageDerivation.canonicalMitigations());

        return new RepairPreparationResult(
                preparedConstraints,
                violatingNodes,
                coverageDerivation.coverageSets(),
                coverageDerivation.contributorMitigations(),
                contradictions,
                reading.cyclic());
    }
}
