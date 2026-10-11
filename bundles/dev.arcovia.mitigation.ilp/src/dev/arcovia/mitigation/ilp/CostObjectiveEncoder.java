package dev.arcovia.mitigation.ilp;

import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import dev.arcovia.mitigation.cost.*;

import java.util.*;

/**
 * Encodes pre-evaluated cost terms as an ILP objective.
 */
public final class CostObjectiveEncoder {

    /**
     * Configures one objective from canonical action variables.
     *
     * @param solver          the solver to configure
     * @param actionVariables canonical action variables
     * @param specification   the declared cost specification
     * @param descriptors     pre-evaluated direct costs by action
     * @return a handle that derives the selected cost breakdown after solving
     */
    public ConfiguredObjective configure(MPSolver solver, Map<RepairActionKey, MPVariable> actionVariables,
                                         RepairCostSpecification specification,
                                         Map<RepairActionKey, ActionCostDescriptor> descriptors) {
        Objects.requireNonNull(solver, "solver must not be null");
        Objects.requireNonNull(actionVariables, "actionVariables must not be null");
        Objects.requireNonNull(specification, "specification must not be null");
        Objects.requireNonNull(descriptors, "descriptors must not be null");

        MPObjective objective = solver.objective();
        Map<RepairActionKey, ActionCostDescriptor> actionDescriptors = new TreeMap<>();
        actionVariables.forEach((actionKey, variable) -> {
            ActionCostDescriptor descriptor = Objects.requireNonNull(descriptors.get(actionKey),
                    () -> "Missing cost descriptor for " + actionKey.stableId());
            actionDescriptors.put(actionKey, descriptor);
            objective.setCoefficient(variable, descriptor.directCost());
        });

        Map<SharedCostGroup, MPVariable> groupVariables = new LinkedHashMap<>();
        int groupIndex = 0;
        for (SharedCostGroup group : specification.sharedCostGroups()) {
            List<Map.Entry<RepairActionKey, MPVariable>> members = group.memberActions().stream()
                    .filter(actionVariables::containsKey)
                    .sorted()
                    .map(actionKey -> Map.entry(actionKey, actionVariables.get(actionKey)))
                    .toList();
            if (members.isEmpty()) {
                continue;
            }
            MPVariable groupVariable = solver.makeIntVar(0, 1, "y_" + groupIndex + "_" + safeName(group.id()));
            groupVariables.put(group, groupVariable);
            objective.setCoefficient(groupVariable, group.fixedCost());
            addSharedGroupConstraints(solver, groupVariable, members, groupIndex++);
        }
        objective.setMinimization();
        return new ConfiguredObjective(specification, actionDescriptors, actionVariables, groupVariables);
    }

    /**
     * Links each selected member action to one shared group variable.
     *
     * @param solver        the solver to configure
     * @param groupVariable the shared group variable
     * @param members       the available member variables
     * @param groupIndex    the unique constraint-name suffix
     */
    private void addSharedGroupConstraints(MPSolver solver, MPVariable groupVariable,
                                           List<Map.Entry<RepairActionKey, MPVariable>> members, int groupIndex) {
        for (int memberIndex = 0; memberIndex < members.size(); memberIndex++) {
            MPConstraint requiresGroup = solver.makeConstraint(Double.NEGATIVE_INFINITY, 0,
                    "shared_requires_" + groupIndex + "_" + memberIndex);
            requiresGroup.setCoefficient(members.get(memberIndex).getValue(), 1);
            requiresGroup.setCoefficient(groupVariable, -1);
        }
        MPConstraint groupMustBeUsed = solver.makeConstraint(Double.NEGATIVE_INFINITY, 0,
                "shared_used_" + groupIndex);
        groupMustBeUsed.setCoefficient(groupVariable, 1);
        members.forEach(member -> groupMustBeUsed.setCoefficient(member.getValue(), -1));
    }

    /**
     * Converts a group identifier into a solver-safe name fragment.
     *
     * @param value the identifier to sanitize
     * @return a solver-safe identifier fragment
     */
    private String safeName(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_]", "_");
        return sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0)) ? "g_" + sanitized : sanitized;
    }

    /**
     * Captures an objective after variables and constraints have been configured.
     */
    public static final class ConfiguredObjective {
        private final RepairCostSpecification specification;
        private final Map<RepairActionKey, ActionCostDescriptor> descriptors;
        private final Map<RepairActionKey, MPVariable> actionVariables;
        private final Map<SharedCostGroup, MPVariable> groupVariables;

        /**
         * Snapshots the configured objective state.
         *
         * @param specification   the declared specification
         * @param descriptors     descriptors by canonical action
         * @param actionVariables action variables by canonical action
         * @param groupVariables  shared-group variables
         */
        private ConfiguredObjective(RepairCostSpecification specification,
                                    Map<RepairActionKey, ActionCostDescriptor> descriptors,
                                    Map<RepairActionKey, MPVariable> actionVariables,
                                    Map<SharedCostGroup, MPVariable> groupVariables) {
            this.specification = specification;
            this.descriptors = Map.copyOf(descriptors);
            this.actionVariables = Map.copyOf(actionVariables);
            this.groupVariables = Map.copyOf(groupVariables);
        }

        /**
         * Returns the selected plan's direct and shared objective costs.
         *
         * @return the immutable objective breakdown
         */
        public ObjectiveCostBreakdown selectedCostBreakdown() {
            List<ActionCostDescriptor> selectedActions = descriptors.entrySet().stream()
                    .filter(entry -> actionVariables.get(entry.getKey()).solutionValue() > 0.5)
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparing(descriptor -> descriptor.actionKey().stableId()))
                    .toList();
            List<SharedCostGroup> selectedGroups = groupVariables.entrySet().stream()
                    .filter(entry -> entry.getValue().solutionValue() > 0.5)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparing(SharedCostGroup::id))
                    .toList();
            double directCost = selectedActions.stream().mapToDouble(ActionCostDescriptor::directCost).sum();
            double sharedCost = selectedGroups.stream().mapToDouble(SharedCostGroup::fixedCost).sum();
            return new ObjectiveCostBreakdown(specification.id(), specification.version(), specification.unit(),
                    selectedActions, selectedGroups.stream().map(SharedCostGroup::id).toList(),
                    directCost, sharedCost, directCost + sharedCost);
        }
    }
}
