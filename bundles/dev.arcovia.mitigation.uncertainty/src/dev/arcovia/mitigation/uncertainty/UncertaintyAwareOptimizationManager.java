package dev.arcovia.mitigation.uncertainty;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.ilp.OptimizationManager;
import dev.arcovia.mitigation.sat.timeMeasurement;
import dev.arcovia.mitigation.uncertainty.RobustRepairResult.ValidationStatus;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import dev.arcovia.mitigation.uncertainty.output.RepairOutput;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparation;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparationService;
import dev.arcovia.mitigation.uncertainty.pruning.ClusterCouplingGuard;
import dev.arcovia.mitigation.uncertainty.pruning.SourceImpactFilter;
import dev.arcovia.mitigation.uncertainty.pruning.SourcePartitioner;
import dev.arcovia.mitigation.uncertainty.solving.*;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationException;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidator;
import org.dataflowanalysis.analysis.dsl.AnalysisConstraint;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.DataDictionary;
import org.dataflowanalysis.dfd.dataflowdiagram.DataFlowDiagram;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


/**
 * Public facade for uncertainty-aware repair.
 *
 * <p>Without an uncertainty model, it delegates to {@link OptimizationManager}.
 */
public class UncertaintyAwareOptimizationManager extends OptimizationManager {

    private final LoadedUncertaintyModel loadedUncertaintyModel;
    private final List<UncertaintySource> selectedUncertaintySources;
    private final List<Constraint> configuredConstraints;
    private final DataFlowDiagramAndDictionary uncertaintyBaseModel;
    private boolean partitioningEnabled = true;
    private List<Mitigation> robustResult = List.of();
    private boolean robustRepairExecuted = false;
    private RobustRepairResult robustRepairResult;
    private RepairOutput repairOutput = RepairOutput.disabled();

    /**
     * Creates a baseline-compatible manager for an in-memory model and DSL constraints.
     *
     * @param dfd         the model to repair
     * @param constraints the confidentiality constraints expressed in the analysis DSL
     */
    public UncertaintyAwareOptimizationManager(@NonNull DataFlowDiagramAndDictionary dfd,
                                               @NonNull List<AnalysisConstraint> constraints) {
        super(dfd, constraints);
        this.loadedUncertaintyModel = null;
        this.selectedUncertaintySources = List.of();
        this.configuredConstraints = constraints.stream()
                .map(Constraint::new)
                .toList();
        this.uncertaintyBaseModel = dfd;
    }

    /**
     * Creates a baseline-compatible manager for an in-memory model and prepared constraints.
     *
     * @param dfd                      the model to repair
     * @param constraints              the already prepared repair constraints
     * @param addAdditionalMitigations whether baseline additional mitigations are enabled
     */
    public UncertaintyAwareOptimizationManager(
            @NonNull DataFlowDiagramAndDictionary dfd,
            @NonNull List<Constraint> constraints,
            boolean addAdditionalMitigations
    ) {
        super(dfd, constraints, addAdditionalMitigations);
        this.loadedUncertaintyModel = null;
        this.selectedUncertaintySources = List.of();
        this.configuredConstraints = List.copyOf(constraints);
        this.uncertaintyBaseModel = dfd;
    }

    /**
     * Creates a baseline-compatible manager that loads a model from disk for DSL constraints.
     *
     * @param dfdLocation the location accepted by the baseline optimization manager
     * @param constraints the confidentiality constraints expressed in the analysis DSL
     */
    public UncertaintyAwareOptimizationManager(@NonNull String dfdLocation,
                                               @NonNull List<AnalysisConstraint> constraints) {
        super(dfdLocation, constraints);
        this.loadedUncertaintyModel = null;
        this.selectedUncertaintySources = List.of();
        this.configuredConstraints = constraints.stream()
                .map(Constraint::new)
                .toList();
        this.uncertaintyBaseModel = null;
    }

    /**
     * Creates a baseline-compatible manager that loads a model from disk for prepared constraints.
     *
     * @param dfdLocation              the location accepted by the baseline optimisation manager
     * @param constraints              the already prepared repair constraints
     * @param addAdditionalMitigations whether baseline additional mitigations are enabled
     */
    public UncertaintyAwareOptimizationManager(@NonNull String dfdLocation, @NonNull List<Constraint> constraints,
                                               boolean addAdditionalMitigations) {
        super(dfdLocation, constraints, addAdditionalMitigations);
        this.loadedUncertaintyModel = null;
        this.selectedUncertaintySources = List.of();
        this.configuredConstraints = List.copyOf(constraints);
        this.uncertaintyBaseModel = null;
    }

    /**
     * Creates a robust-repair manager considering every source declared by a loaded model.
     *
     * @param loadedModel              the loaded uncertainty model
     * @param constraints              the repair constraints to enforce in every scenario
     * @param addAdditionalMitigations whether baseline additional mitigations are enabled
     */
    public UncertaintyAwareOptimizationManager(
            @NonNull LoadedUncertaintyModel loadedModel,
            @NonNull List<Constraint> constraints,
            boolean addAdditionalMitigations
    ) {
        this(loadedModel, constraints, addAdditionalMitigations, loadedModel.sources());
    }

    /**
     * Creates a robust-repair manager restricted to a caller-selected subset of sources.
     *
     * @param loadedModel                the loaded uncertainty model
     * @param constraints                the repair constraints to enforce
     * @param addAdditionalMitigations   whether baseline additional mitigations are enabled
     * @param selectedUncertaintySources the declared sources included in the scenario space
     */
    public UncertaintyAwareOptimizationManager(
            @NonNull LoadedUncertaintyModel loadedModel, @NonNull List<Constraint> constraints,
            boolean addAdditionalMitigations,
            @NonNull List<UncertaintySource> selectedUncertaintySources
    ) {
        super(Objects.requireNonNull(loadedModel, "loadedModel must not be null").baseModel(), constraints,
                addAdditionalMitigations);
        this.loadedUncertaintyModel = loadedModel;
        this.selectedUncertaintySources = List.copyOf(
                Objects.requireNonNull(selectedUncertaintySources, "selectedUncertaintySources must not be null"));
        this.configuredConstraints = List.copyOf(constraints);
        this.uncertaintyBaseModel = loadedModel.baseModel();
    }

    /**
     * Loads an uncertainty model and creates a robust-repair manager for all of its sources.
     *
     * @param uncertaintyModelSpec     the three resource paths that identify the uncertainty model
     * @param constraints              the repair constraints to enforce in every scenario
     * @param addAdditionalMitigations whether baseline additional mitigations are enabled
     */
    public UncertaintyAwareOptimizationManager(
            @NonNull UncertaintyModelSpec uncertaintyModelSpec,
            @NonNull List<Constraint> constraints,
            boolean addAdditionalMitigations
    ) {
        this(new UncertaintyModelLoader().load(uncertaintyModelSpec), constraints, addAdditionalMitigations);
    }

    /**
     * Loads an uncertainty model and creates a robust-repair manager for selected sources only.
     *
     * @param uncertaintyModelSpec       the three resource paths that identify the uncertainty model
     * @param constraints                the repair constraints to enforce in every scenario
     * @param addAdditionalMitigations   whether baseline additional mitigations are enabled
     * @param selectedUncertaintySources the declared sources included in the scenario space
     */
    public UncertaintyAwareOptimizationManager(
            @NonNull UncertaintyModelSpec uncertaintyModelSpec,
            @NonNull List<Constraint> constraints,
            boolean addAdditionalMitigations,
            @NonNull List<UncertaintySource> selectedUncertaintySources
    ) {
        this(new UncertaintyModelLoader().load(uncertaintyModelSpec), constraints, addAdditionalMitigations,
                selectedUncertaintySources);
    }

    /**
     * Determines whether this manager should execute the robust path.
     *
     * @return {@code true} when a loaded uncertainty model is configured; {@code false} for baseline delegation
     */
    private boolean hasLoadedUncertaintyModel() {
        return this.loadedUncertaintyModel != null;
    }

    /**
     * Returns the result from the most recent robust-repair execution.
     *
     * @return the robust result, or empty when no robust repair has completed
     */
    public Optional<RobustRepairResult> getRobustRepairResult() {
        return Optional.ofNullable(robustRepairResult);
    }

    /**
     * Enables output persistence for subsequent robust repairs.
     *
     * @param repairOutput the configured output sink
     */
    public void enableRepairOutput(@NonNull RepairOutput repairOutput) {
        this.repairOutput = Objects.requireNonNull(repairOutput, "repairOutput must not be null");
    }

    /**
     * Toggles A2 independence partitioning (default on). When disabled, the repair always solves and
     * validates over the full scenario product of all relevant sources. Intended for tests that
     * compare the per-cluster path against the whole-set baseline; production callers leave it on.
     *
     * @param partitioningEnabled {@code true} to allow safe decomposition; {@code false} for the full product
     */
    public void setPartitioningEnabled(boolean partitioningEnabled) {
        this.partitioningEnabled = partitioningEnabled;
    }

    /**
     * Executes the robust repair pipeline of the Approach chapter. Stage 1 (loading) has
     * already happened in the constructor; this method runs stages 2--7 in order, each
     * delegated to the component of the corresponding pipeline package.
     *
     * @param timer optional baseline-compatible timing recorder, or {@code null}
     * @return the repaired base model after successful validation
     * @throws Exception if solving, applying, or validation fails
     */
    private DataFlowDiagramAndDictionary repairLoadedUncertainty(timeMeasurement timer) throws Exception {
        RepairOutput output = repairOutput;
        output.record("start-robust-repair", () -> "selectedSources=" + selectedUncertaintySources.size()
                                                   + ", constraints=" + configuredConstraints.size());
        if (timer != null) {
            timer.start();
        }

        // A1 -- impact pruning: drop sources that cannot affect any constraint before enumerating,
        // so the Cartesian product (and the post-repair validation) is taken only over relevant
        // sources. The SAME pruned set must feed both preparation and validation, or validation would
        // re-introduce scenarios the repair never considered.
        List<UncertaintySource> relevantSources = new SourceImpactFilter()
                .retainImpactful(loadedUncertaintyModel.baseModel(), selectedUncertaintySources);
        output.record("prune-sources", () -> "selectedSources=" + selectedUncertaintySources.size()
                                             + ", relevantSources=" + relevantSources.size());

        // Stages 2--4: enumerate the source selections, materialize each selection, and derive
        // its violation coverage and contradiction constraints. ScenarioPreparationService is the
        // explicit hand-off between those three packages and yields one preparation per scenario.
        // A2 -- independence partitioning. Split the relevant sources into clusters that share no
        // constraint-reachable element (SourcePartitioner), then, when the partition is free of
        // cross-cluster ILP coupling (ClusterCouplingGuard), solve and validate over the per-cluster
        // union of scenarios (∑ 2^{n_i}) instead of the full product (∏ 2^{Σ n_i}). The shared
        // RepairActionKey variables compose the per-cluster plans automatically. Any coupling, or a
        // single cluster, falls back to the whole-set solve -- byte-identical to the pre-A2 behavior.
        ScenarioPreparationService scenarioPreparationService = new ScenarioPreparationService();
        RobustRepairValidator repairValidator = new RobustRepairValidator(scenarioPreparationService);

        List<List<UncertaintySource>> clusters = partitioningEnabled
                ? new SourcePartitioner().partition(uncertaintyBaseModel, relevantSources)
                : List.of(relevantSources);
        List<List<ScenarioPreparation>> perClusterPreparations = clusters.stream()
                .map(cluster -> scenarioPreparationService.prepare(uncertaintyBaseModel, cluster, configuredConstraints))
                .toList();
        boolean decomposed = clusters.size() > 1 && new ClusterCouplingGuard().isSafeToDecompose(
                perClusterPreparations.stream()
                        .map(cluster -> cluster.stream().map(ScenarioPreparation::preparation).toList())
                        .toList());
        List<List<UncertaintySource>> clustersUsed = decomposed ? clusters : List.of(relevantSources);
        List<ScenarioPreparation> scenarioPreparations = decomposed
                ? perClusterPreparations.stream().flatMap(List::stream).toList()
                : clusters.size() == 1
                  ? perClusterPreparations.get(0)
                  : scenarioPreparationService.prepare(uncertaintyBaseModel, relevantSources, configuredConstraints);
        boolean decomposedFinal = decomposed;
        output.record("partition-sources", () -> "clusters=" + clusters.size()
                                                 + ", decomposed=" + decomposedFinal
                                                 + ", scenariosConsidered=" + scenarioPreparations.size());
        int preRepairViolationCount = repairValidator.fromPreparations(scenarioPreparations).totalViolationCount();
        List<String> consideredScenarioIds = scenarioPreparations.stream()
                .map(preparation -> preparation.scenario().id())
                .toList();
        output.record("prepare-scenarios", () -> "scenarioCount=" + scenarioPreparations.size()
                                                 + ", scenarioIds=" + consideredScenarioIds
                                                 + ", preRepairViolations=" + preRepairViolationCount
                                                 + ", candidateActions=" + countCandidateActions(scenarioPreparations)
                                                 + ", coverageConstraints=" + countCoverageConstraints(scenarioPreparations));
        if (timer != null) {
            timer.constraints();
            timer.analysis();
        }

        // Stage 5 -- solve the joint ILP over all scenario preparations.
        RobustSolverResult solverResult = solveJointIlp(scenarioPreparations, output);
        robustResult = solverResult.selectedMitigations();
        robustRepairExecuted = true;
        double totalCost = robustResult.stream().mapToDouble(Mitigation::cost).sum();
        output.record("solve-robust-ilp-completed", () -> "solverStatus=" + solverResult.solverStatus()
                                                          + ", selectedActions=" + robustResult.size()
                                                          + ", totalCost=" + totalCost);
        if (timer != null) {
            timer.solving();
        }

        // Stage 6 -- apply the selected actions to a dedicated copy of the base model.
        List<ActionTerm> selectedActions = robustResult.stream()
                .map(Mitigation::mitigation)
                .toList();
        output.record("apply-selected-actions", () -> "actionCount=" + selectedActions.size()
                                                      + ", actions=" + selectedActions);
        DataFlowDiagramAndDictionary repairedModel = applyToRepairedCopy(selectedActions);

        // Stage 7 -- validate the repaired model. When the sources were decomposed, each cluster is
        // validated over its own scenario sub-space; because the clusters are independent, per-cluster
        // validity implies global validity (∑ ⇒ ∏). A single cluster validates over the full product.
        output.record("validate-repaired-model", () -> "scenarioCount=" + consideredScenarioIds.size());
        List<ScenarioPreparation> postRepairPreparations = clustersUsed.stream()
                .flatMap(cluster -> scenarioPreparationService.prepare(repairedModel, cluster, configuredConstraints).stream())
                .toList();
        RobustRepairValidationResult postRepairValidation = repairValidator.fromPreparations(postRepairPreparations);
        ValidationStatus validationStatus = postRepairValidation.passed()
                ? ValidationStatus.PASSED
                : ValidationStatus.FAILED;
        output.record("validate-repaired-model-completed", () -> "validationStatus=" + validationStatus
                                                                 + ", postRepairViolations="
                                                                 + postRepairValidation.totalViolationCount());
        robustRepairResult = new RobustRepairResult(
                repairedModel,
                selectedActions,
                totalCost,
                consideredScenarioIds,
                preRepairViolationCount,
                postRepairValidation.totalViolationCount(),
                solverResult.solverStatus(),
                validationStatus);

        output.persist(robustRepairResult);
        if (timer != null) {
            timer.stop();
        }
        if (!postRepairValidation.passed()) {
            throw new RobustRepairValidationException(robustRepairResult);
        }
        return repairedModel;
    }

    /**
     * Stage 5: detects uncertainty-controlled labels (whose removal materialization would
     * overwrite) and solves the joint ILP over all scenario preparations with those removals
     * forbidden.
     *
     * @param scenarioPreparations the prepared scenarios to solve jointly
     * @param output               the trace sink for stage-5 progress
     * @return the robust solver result
     * @throws NoRobustRepairExistsException if the joint ILP is infeasible
     */
    private RobustSolverResult solveJointIlp(List<ScenarioPreparation> scenarioPreparations,
                                             RepairOutput output) throws NoRobustRepairExistsException {
        Set<UncertaintyControlledLabelDetector.ControlledLabelKey> forbiddenLabels =
                new UncertaintyControlledLabelDetector().detect(
                        uncertaintyBaseModel,
                        scenarioPreparations.stream().map(preparation -> preparation.scenario().model()).toList());
        output.record("solve-robust-ilp", () -> "scenarioCount=" + scenarioPreparations.size()
                                                + ", forbiddenControlledLabels=" + forbiddenLabels.size());
        return new RobustILPSolver().solveWithResult(scenarioPreparations.stream()
                .map(ScenarioPreparation::preparation)
                .toList(), forbiddenLabels);
    }

    /**
     * Stage 6: transactional action application. The actions are applied to a deep copy of
     * the base model, so the caller's input model stays untouched; the repaired copy replaces
     * the input only by being returned after validation passes. On validation failure the
     * (failed) repaired copy remains available through the exception's
     * {@link RobustRepairResult} while the input model remains unmodified.
     *
     * @param selectedActions the actions selected by the robust solver
     * @return a deep-copied model with the selected actions applied
     */
    private DataFlowDiagramAndDictionary applyToRepairedCopy(List<ActionTerm> selectedActions) {
        DataFlowDiagramAndDictionary repairedModel = deepCopy(uncertaintyBaseModel);
        applyActions(repairedModel, selectedActions);
        return repairedModel;
    }

    /**
     * Deep-copies the diagram and dictionary with a single shared {@link EcoreUtil.Copier} so
     * that cross-references between the two trees (node properties, behavior assignments,
     * labels) point into the copied dictionary rather than back into the original.
     *
     * @param model the diagram and dictionary to copy
     * @return an independent diagram-and-dictionary pair with preserved cross-references
     */
    private static DataFlowDiagramAndDictionary deepCopy(DataFlowDiagramAndDictionary model) {
        EcoreUtil.Copier copier = new EcoreUtil.Copier();
        DataFlowDiagram diagramCopy = (DataFlowDiagram) copier.copy(model.dataFlowDiagram());
        DataDictionary dictionaryCopy = (DataDictionary) copier.copy(model.dataDictionary());
        copier.copyReferences();
        return new DataFlowDiagramAndDictionary(diagramCopy, dictionaryCopy);
    }

    /**
     * Counts distinct canonical candidate actions across all prepared scenarios for trace output.
     *
     * @param scenarioPreparations the prepared scenarios to inspect
     * @return the number of distinct semantic repair actions
     */
    private int countCandidateActions(List<ScenarioPreparation> scenarioPreparations) {
        return (int) scenarioPreparations.stream()
                .flatMap(scenarioPreparation -> scenarioPreparation.preparation().allMitigations().stream())
                .map(RepairActionKey::from)
                .distinct()
                .count();
    }

    /**
     * Counts coverage clauses, one for each scenario-and-violation pair.
     *
     * @param scenarioPreparations the prepared scenarios to inspect
     * @return the total number of coverage clauses
     */
    private int countCoverageConstraints(List<ScenarioPreparation> scenarioPreparations) {
        return scenarioPreparations.stream()
                .map(ScenarioPreparation::preparation)
                .mapToInt(preparation -> preparation.mitigations().size())
                .sum();
    }


    /**
     * Returns the cost of the latest robust repair or delegates to the baseline cost otherwise.
     *
     * @return the integer cost reported by the active repair path
     */
    @Override
    public int getCost() {
        if (robustRepairExecuted) {
            return (int) robustResult.stream().mapToDouble(Mitigation::cost).sum();
        }
        return super.getCost();
    }

    /**
     * Repairs the configured model using the robust pipeline when uncertainty is present.
     *
     * @return the repaired model from the robust or baseline path
     * @throws Exception if the selected repair path fails
     */
    @Override
    public DataFlowDiagramAndDictionary repair() throws Exception {
        if (hasLoadedUncertaintyModel()) {
            return repairLoadedUncertainty(null);
        }
        return super.repair();
    }

    /**
     * Repairs the configured model and records the baseline-compatible phase timings.
     *
     * @param timer the timing recorder supplied by the caller
     * @return the repaired model from the robust or baseline path
     * @throws Exception if the selected repair path fails
     */
    @Override
    public DataFlowDiagramAndDictionary repair(timeMeasurement timer) throws Exception {
        if (hasLoadedUncertaintyModel()) {
            return repairLoadedUncertainty(timer);
        }
        return super.repair(timer);
    }

}
