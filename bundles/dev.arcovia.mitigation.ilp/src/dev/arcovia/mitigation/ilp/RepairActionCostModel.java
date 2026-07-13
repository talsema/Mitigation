package dev.arcovia.mitigation.ilp;

import dev.arcovia.mitigation.sat.CompositeLabel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleBiFunction;

public final class RepairActionCostModel {
    private final EnumMap<MitigationType, Double> baseCosts;
    private final ToDoubleBiFunction<MitigationType, Integer> costFunction;

    private RepairActionCostModel(Map<MitigationType, Double> baseCosts, ToDoubleBiFunction<MitigationType, Integer> costFunction) {
        this.baseCosts = new EnumMap<>(baseCosts);
        this.costFunction = costFunction;
    }

    public static RepairActionCostModel standard() {
        return builder()
                .withBaseCost(MitigationType.NodeLabel, 1.0)
                .withBaseCost(MitigationType.DataLabel, 1.0)
                .withBaseCost(MitigationType.DeleteNodeLabel, 1000.0)
                .withBaseCost(MitigationType.DeleteDataLabel, 1000.0)
                .withBaseCost(MitigationType.DeleteFlow, 4.0)
                .withBaseCost(MitigationType.DeleteNode, 6.0)
                .withBaseCost(MitigationType.AddSink, 8.0)
                .withBaseCost(MitigationType.AddNode, 10.0)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public double costFor(MitigationType type, List<CompositeLabel> labels) {
        return costFor(type, labels, 1.0);
    }

    public double costFor(MitigationType type, List<CompositeLabel> labels, double multiplier) {
        Objects.requireNonNull(type, "type must not be null");
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier must not be negative");
        }

        int labelCount = labels == null ? 1 : Math.max(1, labels.size());
        return multiplier * costFunction.applyAsDouble(type, labelCount);
    }

    public static final class Builder {
        private final EnumMap<MitigationType, Double> baseCosts = new EnumMap<>(MitigationType.class);
        private ToDoubleBiFunction<MitigationType, Integer> costFunction;

        private Builder() {
            for (MitigationType type : MitigationType.values()) {
                baseCosts.put(type, 1.0);
            }
        }

        public Builder withBaseCost(MitigationType type, double cost) {
            Objects.requireNonNull(type, "type must not be null");
            if (cost < 0) {
                throw new IllegalArgumentException("cost must not be negative");
            }
            baseCosts.put(type, cost);
            return this;
        }

        public Builder withCostFunction(ToDoubleBiFunction<MitigationType, Integer> costFunction) {
            this.costFunction = Objects.requireNonNull(costFunction, "costFunction must not be null");
            return this;
        }

        public RepairActionCostModel build() {
            return new RepairActionCostModel(
                    new EnumMap<>(baseCosts),
                    costFunction != null ? costFunction : (type, labelCount) -> baseCosts.get(type)
            );
        }
    }
}
