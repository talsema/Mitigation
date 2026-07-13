package dev.arcovia.mitigation.ilp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.dataflowanalysis.analysis.dfd.core.DFDVertex;

import dev.arcovia.mitigation.sat.CompositeLabel;

public class MitigationStrategy {
	List<CompositeLabel> label;
	double cost;
	MitigationType type;
	List<List<MitigationStrategy>> required = new ArrayList<>();
	List<Constraint> notAllowedIfViolated = new ArrayList<>();

	public MitigationStrategy(List<CompositeLabel> label, double cost, MitigationType type) {
		this.label = label;
		this.cost = cost;
		this.type = type;
	}

	public static MitigationStrategy withCostModel(List<CompositeLabel> label, MitigationType type,
			RepairActionCostModel costModel) {
		return withCostModel(label, 1.0, type, costModel);
	}

	public static MitigationStrategy withCostModel(List<CompositeLabel> label, double multiplier, MitigationType type,
			RepairActionCostModel costModel) {
		Objects.requireNonNull(costModel, "costModel must not be null");
		return new MitigationStrategy(label, costModel.costFor(type, label, multiplier), type);
	}

	public List<CompositeLabel> labels() {
		return List.copyOf(label);
	}

	public double cost() {
		return cost;
	}

	public MitigationType type() {
		return type;
	}

	public MitigationStrategy copy() {
		MitigationStrategy copy = new MitigationStrategy(List.copyOf(label), cost, type);
		copy.required = required.stream()
				.map(clause -> clause.stream()
						.map(MitigationStrategy::copy)
						.collect(Collectors.toCollection(ArrayList::new)))
				.collect(Collectors.toCollection(ArrayList::new));
		copy.notAllowedIfViolated = new ArrayList<>(notAllowedIfViolated);
		return copy;
	}

	public void addRequired(List<List<MitigationStrategy>> required) {
		for (var mitigations : required) {
			List<MitigationStrategy> requiredMitigations = new ArrayList<>();
			for (var mitigation : mitigations) {
				if (!mitigation.type.toString().startsWith("Delete")) {
					requiredMitigations.add(mitigation);
				}
			}
			if (!requiredMitigations.isEmpty()) {
				this.required.add(requiredMitigations);
			}

		}
	}

	public void addConstraint(Constraint constraint) {
		notAllowedIfViolated.add(constraint);
	}

	public boolean checkIfAllowed(DFDVertex vertex) {
		for (var constraint : notAllowedIfViolated) {
			if (constraint.isMatched(vertex)) {
				return false;
			}
		}
		return true;
	}
}
