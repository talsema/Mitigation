package dev.arcovia.mitigation.uncertainty.pipeline;

import dev.arcovia.mitigation.uncertainty.materialization.MaterializedScenario;
import dev.arcovia.mitigation.uncertainty.preparation.RepairPreparationResult;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Objects;

/**
 * Pairs one materialized scenario with the repair preparation derived from it in stage 4.
 *
 * @param scenario    the materialized scenario model
 * @param preparation the violations, coverage sets, and contradictions derived from that scenario
 */
public record ScenarioPreparation(
        @NonNull MaterializedScenario scenario,
        @NonNull RepairPreparationResult preparation
) {

    /**
     * Validates the two stage hand-off values.
     *
     * @param scenario    the materialized scenario
     * @param preparation the preparation derived from that scenario
     */
    public ScenarioPreparation {
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(preparation, "preparation must not be null");
    }
}
