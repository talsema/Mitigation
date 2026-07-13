package dev.arcovia.mitigation.uncertainty.solving;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.ActionType;
import dev.arcovia.mitigation.ilp.ILPSolver;
import dev.arcovia.mitigation.ilp.Mitigation;
import dev.arcovia.mitigation.sat.LabelCategory;
import dev.arcovia.mitigation.uncertainty.preparation.RepairPreparationResult;
import dev.arcovia.mitigation.uncertainty.solving.UncertaintyControlledLabelDetector.ControlledLabelKey;
import org.apache.log4j.Logger;
import org.eclipse.jdt.annotation.NonNull;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Builds and solves the joint robust-repair ILP. Pipeline stage 5.
 */
public final class RobustILPSolver {

    private static final Logger LOGGER = Logger.getLogger(RobustILPSolver.class);

    /**
     * Solves one joint robust-repair ILP and excludes removals that scenario materialization would undo.
     *
     * @param scenarioPreparations the non-empty stage-4 preparations for all considered scenarios
     * @param forbiddenLabels      alternative-injected labels that must not be removed
     * @return the selected canonical mitigations and solver statistics
     * @throws IllegalArgumentException      if {@code scenarioPreparations} is empty
     * @throws NoRobustRepairExistsException if no feasible robust repair exists
     * @throws IllegalStateException         if SCIP cannot be created
     */
    public RobustSolverResult solveWithResult(@NonNull List<RepairPreparationResult> scenarioPreparations,
                                              @NonNull Set<ControlledLabelKey> forbiddenLabels)
            throws NoRobustRepairExistsException {
        Objects.requireNonNull(scenarioPreparations, "scenarioPreparations must not be null");
        Objects.requireNonNull(forbiddenLabels, "forbiddenLabels must not be null");
        if (scenarioPreparations.isEmpty()) {
            throw new IllegalArgumentException("scenarioPreparations must not be empty");
        }

        ILPSolver.ensureNativeLibrariesLoaded();

        Map<RepairActionKey, Mitigation> canonicalMitigations = collectCanonicalMitigations(scenarioPreparations);
        MPSolver solver = MPSolver.createSolver("SCIP_MIXED_INTEGER_PROGRAMMING");
        if (solver == null) {
            throw new IllegalStateException(
                    "SCIP solver is unavailable: OR-Tools could not create a SCIP_MIXED_INTEGER_PROGRAMMING solver. "
                    + "Check that the OR-Tools native libraries are loaded.");
        }
        Map<RepairActionKey, MPVariable> mitigationVariables =
                createMitigationVariables(solver, canonicalMitigations, forbiddenLabels);

        scenarioPreparations.forEach(scenarioPreparation -> addCoverageConstraints(solver, mitigationVariables, scenarioPreparation));
        addContradictionConstraints(solver, mitigationVariables, scenarioPreparations);
        addRequiredConstraints(solver, mitigationVariables, scenarioPreparations);

        MPObjective objective = solver.objective();
        canonicalMitigations.forEach((key, value) -> objective.setCoefficient(mitigationVariables.get(key), value.cost()));
        objective.setMinimization();

        MPSolver.ResultStatus status = solver.solve();
        if (status != MPSolver.ResultStatus.OPTIMAL && status != MPSolver.ResultStatus.FEASIBLE) {
            throw new NoRobustRepairExistsException(
                    "No robust repair exists for " + scenarioPreparations.size()
                    + " prepared scenarios (SCIP status: " + status + ").",
                    status.name());
        }
        if (status == MPSolver.ResultStatus.FEASIBLE) {
            LOGGER.warn("SCIP returned FEASIBLE instead of OPTIMAL: the selected repair plan is valid but may not be cost-minimal.");
        }

        List<Mitigation> selectedMitigations = mitigationVariables.entrySet().stream()
                .filter(entry -> entry.getValue().solutionValue() > 0.5)
                .map(Map.Entry::getKey)
                .map(canonicalMitigations::get)
                .toList();
        return new RobustSolverResult(selectedMitigations, status.name(),
                solver.numVariables(), solver.numConstraints());
    }

    /**
     * Keeps the lowest-cost mitigation for each action key.
     *
     * @param scenarioPreparations the preparations to collect from
     * @return canonical actions indexed by key
     */
    private Map<RepairActionKey, Mitigation> collectCanonicalMitigations(List<RepairPreparationResult> scenarioPreparations) {
        return scenarioPreparations.stream()
                .flatMap(scenarioPreparation -> scenarioPreparation.allMitigations().stream())
                .collect(Collectors.toMap(
                        this::actionKey,
                        mitigation -> mitigation,
                        BinaryOperator.minBy(Comparator.comparingDouble(Mitigation::cost)),
                        TreeMap::new
                ));
    }

    /**
     * Creates one binary variable for each canonical action.
     *
     * @param solver               the solver to populate
     * @param canonicalMitigations the available actions
     * @param forbiddenLabels      labels that cannot be removed
     * @return action variables indexed by action key
     */
    private Map<RepairActionKey, MPVariable> createMitigationVariables(MPSolver solver,
                                                                       Map<RepairActionKey, Mitigation> canonicalMitigations, Set<ControlledLabelKey> forbiddenLabels) {
        Map<RepairActionKey, MPVariable> mitigationVariables = new TreeMap<>();
        int i = 0;
        for (Map.Entry<RepairActionKey, Mitigation> entry : canonicalMitigations.entrySet()) {
            // Forbidden actions get an upper bound of 0: the variable still exists (so all
            // coverage / contradiction / required references remain valid), but the solver
            // cannot select it. A coverage set consisting only of forbidden actions therefore
            // becomes infeasible, surfacing as NoRobustRepairExistsException.
            double upperBound = isForbidden(entry.getValue().mitigation(), forbiddenLabels) ? 0 : 1;
            mitigationVariables.put(entry.getKey(),
                    solver.makeIntVar(0, upperBound, "x_" + i + "_" + safeName(entry.getKey().stableId())));
            i++;
        }
        return mitigationVariables;
    }

    /**
     * Checks whether an action removes an alternative-injected label.
     *
     * @param action          the candidate action
     * @param forbiddenLabels labels that cannot be removed
     * @return {@code true} when the action is forbidden
     */
    private boolean isForbidden(ActionTerm action, Set<ControlledLabelKey> forbiddenLabels) {
        if (forbiddenLabels.isEmpty() || action.type() != ActionType.Removing) {
            return false;
        }
        return action.compositeLabels().stream()
                .filter(compositeLabel -> compositeLabel.category() == LabelCategory.Node
                                          || compositeLabel.category() == LabelCategory.OutgoingData)
                .map(compositeLabel -> new ControlledLabelKey(
                        action.domain(), compositeLabel.label().type(), compositeLabel.label().value()))
                .anyMatch(forbiddenLabels::contains);
    }

    /**
     * Adds one coverage clause for each violation.
     *
     * @param solver              the solver to populate
     * @param mitigationVariables the action variables
     * @param scenarioPreparation the scenario coverage alternatives
     */
    private void addCoverageConstraints(MPSolver solver, Map<RepairActionKey, MPVariable> mitigationVariables, RepairPreparationResult scenarioPreparation) {
        List<List<Mitigation>> mitigations = scenarioPreparation.mitigations();
        IntStream.range(0, mitigations.size()).forEach(i -> {
            List<Mitigation> coverageAlternatives = mitigations.get(i);
            MPConstraint cover = solver.makeConstraint(1.0, Double.POSITIVE_INFINITY, "cover_" + i);
            coverageAlternatives.forEach(mitigation -> cover.setCoefficient(getMitigationVariable(mitigationVariables, mitigation), 1.0));
        });
    }

    /**
     * Adds mutually exclusive action pairs.
     *
     * @param solver               the solver to populate
     * @param mitigationVariables  the action variables
     * @param scenarioPreparations the scenario contradictions
     */
    private void addContradictionConstraints(MPSolver solver, Map<RepairActionKey, MPVariable> mitigationVariables, List<RepairPreparationResult> scenarioPreparations) {
        int counter = 0;

        Set<ContradictionKey> seenContradictions = new LinkedHashSet<>();

        for (RepairPreparationResult scenarioPreparation : scenarioPreparations) {
            for (List<Mitigation> contradiction : scenarioPreparation.contradictions()) {
                if (contradiction.size() != 2) {
                    continue;
                }

                ContradictionKey key = new ContradictionKey(actionKey(contradiction.get(0)),
                        actionKey(contradiction.get(1)));
                if (!seenContradictions.add(key)) {
                    continue;
                }

                MPConstraint conflict = solver.makeConstraint(Double.NEGATIVE_INFINITY, 1.0, "conflict_" + counter++);
                conflict.setCoefficient(getMitigationVariable(mitigationVariables, contradiction.get(0)), 1.0);
                conflict.setCoefficient(getMitigationVariable(mitigationVariables, contradiction.get(1)), 1.0);
            }
        }

    }

    /**
     * Adds required-action clauses.
     *
     * @param solver               the solver to populate
     * @param mitigationVariables  the action variables
     * @param scenarioPreparations the scenario requirements
     */
    private void addRequiredConstraints(MPSolver solver, Map<RepairActionKey, MPVariable> mitigationVariables,
                                        List<RepairPreparationResult> scenarioPreparations) {
        int counter = 0;
        for (int scenarioIndex = 0; scenarioIndex < scenarioPreparations.size(); scenarioIndex++) {
            RepairPreparationResult scenarioPreparation = scenarioPreparations.get(scenarioIndex);

            for (Mitigation mitigation : scenarioPreparation.allMitigations()) {
                counter = enforceMitigationRequirements(solver, mitigationVariables, counter, mitigation, scenarioIndex);
            }
        }
    }

    /**
     * Adds the required-action encoding for one mitigation.
     *
     * @param solver              the solver to populate
     * @param mitigationVariables the action variables
     * @param counter             the next constraint-name suffix
     * @param mitigation          the action with requirements
     * @param scenarioIndex       the scenario index
     * @return the next unused constraint-name suffix
     */
    private int enforceMitigationRequirements(MPSolver solver, Map<RepairActionKey, MPVariable> mitigationVariables, int counter, Mitigation mitigation, int scenarioIndex) {
        MPVariable mitigationVariable = getMitigationVariable(mitigationVariables, mitigation);
        List<MPVariable> requiredAlternatives = new ArrayList<>();
        int clauseIndex = 0;

        for (List<Mitigation> clause : mitigation.required()) {
            MPVariable parentElement = solver.makeIntVar(0, 1,
                    "required_s" + scenarioIndex + "_" + safeName(actionKey(mitigation).stableId()) + "_"
                    + clauseIndex);
            requiredAlternatives.add(parentElement);

            for (Mitigation childMitigation : clause) {
                MPVariable childVariable = getMitigationVariable(mitigationVariables, childMitigation);
                MPConstraint childEnforcement = solver.makeConstraint(Double.NEGATIVE_INFINITY, 0.0,
                        "required_child_" + counter++);
                childEnforcement.setCoefficient(parentElement, 1.0);
                childEnforcement.setCoefficient(childVariable, -1.0);
            }

            int clauseSize = clause.size();
            MPConstraint childrenConstraint = solver.makeConstraint(-(clauseSize - 1), Double.POSITIVE_INFINITY, "required_parent_" + (counter++));
            childrenConstraint.setCoefficient(parentElement, 1.0);

            for (Mitigation childMitigation : clause) {
                childrenConstraint.setCoefficient(getMitigationVariable(mitigationVariables, childMitigation), -1.0);
            }

            clauseIndex++;
        }

        if (!requiredAlternatives.isEmpty()) {
            MPConstraint required = solver.makeConstraint(0.0, Double.POSITIVE_INFINITY, "required_cover_" + counter);
            counter++;
            for (MPVariable parent : requiredAlternatives) {
                required.setCoefficient(parent, 1.0);
            }
            required.setCoefficient(mitigationVariable, -1.0);
        }
        return counter;
    }

    /**
     * Looks up a mitigation's canonical solver variable.
     *
     * @param mitigationVariables the action variables
     * @param mitigation          the mitigation to look up
     * @return its solver variable
     * @throws IllegalStateException if no variable exists
     */
    private MPVariable getMitigationVariable(Map<RepairActionKey, MPVariable> mitigationVariables, Mitigation mitigation) {
        MPVariable variable = mitigationVariables.get(actionKey(mitigation));
        if (variable == null) {
            throw new IllegalStateException("Missing canonical variable for mitigation " + mitigation.mitigation());
        }
        return variable;
    }

    /**
     * Returns a mitigation's canonical action key.
     *
     * @param mitigation the mitigation to identify
     * @return its action key
     */
    private RepairActionKey actionKey(Mitigation mitigation) {
        return RepairActionKey.from(mitigation);
    }

    /**
     * Converts text into a solver-safe variable-name fragment.
     *
     * @param value the text to sanitize
     * @return an identifier-safe name
     */
    private static String safeName(String value) {
        String sanitized = value.trim().replaceAll("\\s+", "_").replaceAll("[^A-Za-z0-9_]", "_");
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            return "x_" + sanitized;
        }
        return sanitized;
    }

    /**
     * Order-independent identity for one conflicting pair of canonical actions.
     *
     * @param left  one action in the pair
     * @param right the other action in the pair
     */
    private record ContradictionKey(@NonNull RepairActionKey left, @NonNull RepairActionKey right) {
        /**
         * Normalizes the pair so equal contradiction pairs share one set key.
         *
         * @param left  one action in the pair
         * @param right the other action in the pair
         */
        private ContradictionKey(RepairActionKey left, RepairActionKey right) {
            if (left.compareTo(right) <= 0) {
                this.left = left;
                this.right = right;
            } else {
                this.left = right;
                this.right = left;
            }
        }
    }
}
