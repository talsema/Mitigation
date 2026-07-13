package dev.arcovia.mitigation.uncertainty.pipeline;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioCombinationGenerator;
import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioSelection;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.materialization.MaterializedScenario;
import dev.arcovia.mitigation.uncertainty.materialization.ScenarioMaterializer;
import dev.arcovia.mitigation.uncertainty.preparation.RepairPreparationService;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.eclipse.jdt.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Connects enumeration, materialization, and preparation. Pipeline stages 2-4.
 */
public final class ScenarioPreparationService {
    private final ScenarioCombinationGenerator combinationGenerator = new ScenarioCombinationGenerator();
    private final ScenarioMaterializer materializer = new ScenarioMaterializer();
    private final RepairPreparationService repairPreparationService = new RepairPreparationService();

    /**
     * Prepares all combinations of selected sources from a loaded model.
     *
     * @param loadedModel     the loaded model
     * @param selectedSources the sources to combine
     * @param constraints     the repair constraints
     * @return one preparation per scenario
     */
    public List<ScenarioPreparation> prepare(
            @NonNull LoadedUncertaintyModel loadedModel,
            @NonNull List<UncertaintySource> selectedSources,
            @NonNull List<Constraint> constraints
    ) {
        Objects.requireNonNull(loadedModel, "loadedModel must not be null");
        return prepare(loadedModel.baseModel(), selectedSources, constraints);
    }

    /**
     * Prepares all combinations of selected sources from a base model.
     *
     * @param baseModel       the model to materialize
     * @param selectedSources the sources to combine
     * @param constraints     the repair constraints
     * @return one preparation per scenario, or one base preparation when no source is selected
     */
    public List<ScenarioPreparation> prepare(
            @NonNull DataFlowDiagramAndDictionary baseModel,
            @NonNull List<UncertaintySource> selectedSources,
            @NonNull List<Constraint> constraints
    ) {
        Objects.requireNonNull(baseModel, "baseModel must not be null");
        Objects.requireNonNull(selectedSources, "selectedSources must not be null");
        Objects.requireNonNull(constraints, "constraints must not be null");

        if (selectedSources.isEmpty()) {
            MaterializedScenario baseScenario = new MaterializedScenario("base", baseModel, List.of());
            return List.of(new ScenarioPreparation(baseScenario, repairPreparationService.prepare(baseModel, constraints)));
        }

        List<List<ScenarioSelection>> combinations = combinationGenerator.generate(selectedSources);
        List<ScenarioPreparation> preparedScenarios = new ArrayList<>(combinations.size());
        preparedScenarios.add(prepareScenario(baseModel, combinations.get(0), constraints));
        preparedScenarios.addAll(combinations.subList(1, combinations.size()).parallelStream()
                .map(combination -> prepareScenario(baseModel, combination, constraints))
                .toList());
        return preparedScenarios;
    }

    /**
     * Materializes one enumerated selection and derives its stage-4 repair input.
     *
     * @param baseModel   the unmaterialized model from which to derive the scenario
     * @param combination the selections that identify the scenario
     * @param constraints the repair constraints to prepare against the scenario
     * @return the materialized scenario paired with its repair preparation
     */
    private ScenarioPreparation prepareScenario(
            DataFlowDiagramAndDictionary baseModel,
            List<ScenarioSelection> combination,
            List<Constraint> constraints
    ) {
        MaterializedScenario materializedScenario = materializer.materialize(baseModel, combination);
        return new ScenarioPreparation(
                materializedScenario,
                repairPreparationService.prepare(materializedScenario.model(), constraints));
    }
}
