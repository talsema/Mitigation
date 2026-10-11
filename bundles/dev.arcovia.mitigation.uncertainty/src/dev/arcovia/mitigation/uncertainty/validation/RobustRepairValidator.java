package dev.arcovia.mitigation.uncertainty.validation;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparation;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparationService;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult.ScenarioValidation;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Validates a repaired model in every considered scenario. Pipeline stage 7.
 */
public final class RobustRepairValidator {
    private final ScenarioPreparationService scenarioPreparationService;

    /**
     * Creates a validator using the supplied scenario-preparation boundary.
     *
     * @param scenarioPreparationService the service used to materialize and prepare validation scenarios
     */
    public RobustRepairValidator(@NonNull ScenarioPreparationService scenarioPreparationService) {
        this.scenarioPreparationService = Objects.requireNonNull(
                scenarioPreparationService, "scenarioPreparationService must not be null");
    }

    /**
     * Validates a repaired model across all selected-source combinations.
     *
     * @param repairedModel   the model to validate
     * @param selectedSources the sources to materialize
     * @param constraints     the constraints to check
     * @return the per-scenario validation result
     */
    public RobustRepairValidationResult validate(
            @NonNull DataFlowDiagramAndDictionary repairedModel,
            @NonNull List<UncertaintySource> selectedSources,
            @NonNull List<Constraint> constraints
    ) {
        Objects.requireNonNull(repairedModel, "repairedModel must not be null");
        Objects.requireNonNull(selectedSources, "selectedSources must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        List<ScenarioPreparation> postRepairPreparations = scenarioPreparationService.prepare(
                repairedModel,
                selectedSources,
                constraints
        );
        return fromPreparations(postRepairPreparations);
    }

    /**
     * Converts prepared scenarios into validation results.
     *
     * @param scenarioPreparations the scenarios to summarize
     * @return the per-scenario validation result
     */
    public RobustRepairValidationResult fromPreparations(@NonNull List<ScenarioPreparation> scenarioPreparations) {
        Objects.requireNonNull(scenarioPreparations, "scenarioPreparations must not be null");
        return new RobustRepairValidationResult(scenarioPreparations.stream()
                .map(this::toScenarioValidation)
                .toList());
    }

    /**
     * Converts one prepared scenario into its validation summary.
     *
     * @param scenarioPreparation the scenario and its post-repair preparation result
     * @return the scenario id and its residual targeted-violation count
     */
    private ScenarioValidation toScenarioValidation(ScenarioPreparation scenarioPreparation) {
        return new ScenarioValidation(
                scenarioPreparation.scenario().id(),
                scenarioPreparation.preparation().violatingNodes().size());
    }
}
