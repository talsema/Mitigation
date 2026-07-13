package dev.arcovia.mitigation.uncertainty.integration;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.ilp.MitigationStrategy;
import dev.arcovia.mitigation.ilp.MitigationType;
import dev.arcovia.mitigation.ilp.OptimizationManager;
import dev.arcovia.mitigation.sat.Label;
import dev.arcovia.mitigation.sat.NodeLabel;
import dev.arcovia.mitigation.uncertainty.UncertaintyAwareOptimizationManager;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparationService;
import dev.arcovia.mitigation.uncertainty.solving.NoRobustRepairExistsException;
import org.dataflowanalysis.analysis.dsl.AnalysisConstraint;
import org.dataflowanalysis.analysis.dsl.constraint.ConstraintDSL;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustRepairPipelineTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    private final String minDfd = current.resolve("models")
            .resolve("minsat.json")
            .toString();

    @Test
    public void emptyUncertaintyDelegatesToCurrentIlpRepair() throws Exception {
        var baselineOptimization = new OptimizationManager(minDfd, List.of(createDeleteNodeConstraint()), false);
        var baselineResult = baselineOptimization.repair();

        var uncertaintyAwareOptimization = new UncertaintyAwareOptimizationManager(minDfd,
                List.of(createDeleteNodeConstraint()), false);
        var uncertaintyAwareResult = uncertaintyAwareOptimization.repair();

        assertTrue(baselineOptimization.isViolationFree(baselineResult));
        assertTrue(uncertaintyAwareOptimization.isViolationFree(uncertaintyAwareResult));
        assertEquals(baselineOptimization.getCost(), uncertaintyAwareOptimization.getCost());
        assertEquals(baselineResult.dataFlowDiagram().getNodes().size(),
                uncertaintyAwareResult.dataFlowDiagram().getNodes().size());
        assertEquals(baselineResult.dataFlowDiagram().getFlows().size(),
                uncertaintyAwareResult.dataFlowDiagram().getFlows().size());
    }

    @Test
    public void surfacesNoRobustRepairExistsForInfeasibleRepair() {
        var loadedModel = loadExternal();
        var selectedSource = sourcesNamed(loadedModel, "Banking_Data_Location_Uncertain");

        var infeasibleConstraint = new Constraint(personalToNonEuDsl(), List.of());

        var initialPreparations = new ScenarioPreparationService().prepare(loadedModel.baseModel(), selectedSource,
                List.of(infeasibleConstraint));
        assertTrue(initialPreparations.stream()
                .anyMatch(preparation -> !preparation.preparation().violatingNodes().isEmpty()));

        var optimization = new UncertaintyAwareOptimizationManager(loadedModel, List.of(infeasibleConstraint), false,
                selectedSource);

        var exception = Assertions.assertThrows(NoRobustRepairExistsException.class,
                optimization::repair);

        assertTrue(exception.getMessage().contains("No robust repair exists"));
        assertTrue(exception.solverStatus().isPresent());
    }

    @Test
    public void repairsSelectedAcrossAllMaterializedScenarios() throws Exception {
        var loadedModel = loadExternal();
        var selectedSource = sourcesNamed(loadedModel, "Banking_Data_Location_Uncertain");

        var confidentialityConstraint = new Constraint(personalToNonEuDsl());

        var initialPreparations = new ScenarioPreparationService().prepare(loadedModel.baseModel(), selectedSource,
                List.of(confidentialityConstraint));
        assertTrue(initialPreparations.stream()
                .anyMatch(preparation -> !preparation.preparation().violatingNodes().isEmpty()));

        var optimization = new UncertaintyAwareOptimizationManager(loadedModel, List.of(confidentialityConstraint), false,
                selectedSource);

        var repairedModel = optimization.repair();
        var scenarioPreparations = new ScenarioPreparationService().prepare(repairedModel, selectedSource,
                List.of(confidentialityConstraint));

        assertEquals(2, scenarioPreparations.size());
        assertTrue(scenarioPreparations.stream()
                .allMatch(preparation -> preparation.preparation().violatingNodes().isEmpty()));
    }

    @Test
    public void robustRepairCoversAllScenariosWhereSingleScenarioRepairDoesNot() throws Exception {
        var loadedModel = loadExternal();
        // Banking_Data_Location_Uncertain: default = nonEU (violation), alt = EU (clean)
        var selectedSource = sourcesNamed(loadedModel, "Banking_Data_Location_Uncertain");

        var constraint = new Constraint(personalToNonEuDsl());

        // verify scenario structure
        var initialPreparations = new ScenarioPreparationService().prepare(
                loadedModel, selectedSource, List.of(constraint));
        assertEquals(2, initialPreparations.size(), "Expected exactly two scenarios");
        assertTrue(initialPreparations.stream().anyMatch(sp -> !sp.preparation().violatingNodes().isEmpty()),
                "Default scenario (Banking_DB=nonEU) must have at least one violation");
        assertTrue(initialPreparations.stream().anyMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Alt scenario (Banking_DB=EU) must be violation-free");

        // scenario repair: only repair the violation-free alt scenario
        var violationFreeScenario = initialPreparations.stream()
                .filter(sp -> sp.preparation().violatingNodes().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a violation-free scenario"));

        // no violations → the optimizer finds nothing to fix → base model is unchanged
        var singleScenarioOptimizer = new OptimizationManager(
                violationFreeScenario.scenario().model(), List.of(constraint), false);
        singleScenarioOptimizer.repair();

        // single-scenario repair leaves the base model violated
        var afterSingleRepairPreparations = new ScenarioPreparationService().prepare(
                loadedModel.baseModel(), selectedSource, List.of(constraint));
        assertTrue(
                afterSingleRepairPreparations.stream().anyMatch(sp -> !sp.preparation().violatingNodes().isEmpty()),
                "After single-scenario repair on the violation-free scenario the base model "
                + "must still contain at least one violated scenario");

        // eliminates violations in ALL scenarios
        var robustOptimization = new UncertaintyAwareOptimizationManager(
                loadedModel, List.of(constraint), false, selectedSource);
        var robustRepaired = robustOptimization.repair();

        var afterRobustRepairPreparations = new ScenarioPreparationService().prepare(
                robustRepaired, selectedSource, List.of(constraint));
        assertEquals(2, afterRobustRepairPreparations.size());
        assertTrue(
                afterRobustRepairPreparations.stream().allMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Robust repair must produce a violation-free result in every materialised scenario");
    }

    @Test
    public void robustRepairCoversAllCartesianProductScenariosAcrossTwoUncertaintySources() throws Exception {
        var loadedModel = loadExternal();
        var selectedSources = sourcesNamed(loadedModel,
                "Banking_Data_Location_Uncertain", "User_Location_Uncertain");

        var constraint = new Constraint(personalToNonEuDsl());

        // verify cartesian-product scenario generation
        var initialPreparations = new ScenarioPreparationService().prepare(
                loadedModel, selectedSources, List.of(constraint));

        assertEquals(4, initialPreparations.size(),
                "Two sources with one alternative each must yield 2 × 2 = 4 scenario combinations");

        long violatedCount = initialPreparations.stream()
                .filter(sp -> !sp.preparation().violatingNodes().isEmpty())
                .count();
        long cleanCount = initialPreparations.stream()
                .filter(sp -> sp.preparation().violatingNodes().isEmpty())
                .count();

        assertEquals(2, violatedCount,
                "Exactly the two Banking=nonEU combinations must carry violations");
        assertEquals(2, cleanCount,
                "Exactly the two Banking=EU combinations must be violation-free");

        // verify that violated scenarios correspond to Banking=default (nonEU)
        assertTrue(initialPreparations.stream()
                        .filter(sp -> !sp.preparation().violatingNodes().isEmpty())
                        .allMatch(sp -> sp.scenario().id().contains("Banking_Data_Location_Uncertain:default")),
                "Every violated scenario must be one where Banking_DB retains its nonEU base label");

        // must be violation-free in all four scenarios
        var robustOptimization = new UncertaintyAwareOptimizationManager(
                loadedModel, List.of(constraint), false, selectedSources);
        var robustRepaired = robustOptimization.repair();

        var afterRobustRepairPreparations = new ScenarioPreparationService().prepare(
                robustRepaired, selectedSources, List.of(constraint));

        assertEquals(4, afterRobustRepairPreparations.size());
        assertTrue(
                afterRobustRepairPreparations.stream().allMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Robust repair must eliminate violations across all four Cartesian-product scenarios");
    }

    @Test
    public void robustDeleteNodeRepairEliminatesViolatingNodesAcrossAllMaterializedScenarios() throws Exception {
        var loadedModel = loadExternal();

        // each source contributes a violated node in different scenarios.
        var selectedSources = sourcesNamed(loadedModel,
                "Banking_Data_Location_Uncertain", "Transactions_DB_Location_Uncertain");

        // only structural deletion is offered - no label manipulation.
        var deleteNodeConstraint = new Constraint(personalToNonEuDsl(),
                List.of(new MitigationStrategy(
                        List.of(new NodeLabel(new Label("Location", "nonEU"))), 1,
                        MitigationType.DeleteNode)));

        // Verify scenario structure
        var initialPreparations = new ScenarioPreparationService().prepare(
                loadedModel, selectedSources, List.of(deleteNodeConstraint));

        assertEquals(4, initialPreparations.size(),
                "Two binary sources must produce 2 × 2 = 4 scenario combinations");
        assertEquals(3, initialPreparations.stream()
                        .filter(sp -> !sp.preparation().violatingNodes().isEmpty()).count(),
                "Scenarios 1, 2, 4 must each carry at least one violation");
        assertEquals(1, initialPreparations.stream()
                        .filter(sp -> sp.preparation().violatingNodes().isEmpty()).count(),
                "Scenario 3 (Banking=EU, Trans=EU) must be the only clean combination");

        // Single-scenario repair on the clean scenario
        var violationFreeScenario = initialPreparations.stream()
                .filter(sp -> sp.preparation().violatingNodes().isEmpty())
                .findFirst()
                .orElseThrow();

        // Clean scenario → no violations → no deletions → base model unchanged.
        new OptimizationManager(
                violationFreeScenario.scenario().model(),
                List.of(deleteNodeConstraint), false).repair();

        var afterSingleRepair = new ScenarioPreparationService().prepare(
                loadedModel.baseModel(), selectedSources, List.of(deleteNodeConstraint));
        assertTrue(
                afterSingleRepair.stream().anyMatch(sp -> !sp.preparation().violatingNodes().isEmpty()),
                "Repair on the violation-free scenario must leave other scenarios still violated");

        // must delete both Banking_DB and Transactions_DB
        var robustOptimization = new UncertaintyAwareOptimizationManager(
                loadedModel, List.of(deleteNodeConstraint), false, selectedSources);
        var robustRepaired = robustOptimization.repair();

        assertEquals(2, robustOptimization.getCost(),
                "Robust repair must structurally delete exactly two nodes (cost 1 each)");

        var afterRobustRepair = new ScenarioPreparationService().prepare(
                robustRepaired, selectedSources, List.of(deleteNodeConstraint));
        assertEquals(4, afterRobustRepair.size());
        assertTrue(
                afterRobustRepair.stream().allMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Robust DeleteNode repair must be violation-free in every materialized scenario, "
                + "including those where the deleted node was the uncertainty target"
        );
    }

    @Test
    public void robustRepairRunsEndToEndWithInterfaceUncertaintySources() throws Exception {
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                interfaceModelFolder().resolve("int.dataflowdiagram"),
                interfaceModelFolder().resolve("int.datadictionary"),
                interfaceModelFolder().resolve("int.uncertainty")));

        // Interface uncertainty: loan_flow destination is uncertain (request_loan
        // vs. request_loan2). Violations arise from nodes not affected by this
        // routing, so both scenarios carry violations.
        var selectedSource = loadedModel.sources().stream()
                .filter(s -> s.getEntityName().equals("loan_req_uncertain"))
                .toList();
        assertEquals(1, selectedSource.size(), "Expected exactly one 'loan_req_uncertain' source");

        var constraint = new Constraint(personalToNonEuDsl());

        // Two scenarios (default + alt) must both be prepared.
        var initialPreparations = new ScenarioPreparationService().prepare(
                loadedModel, selectedSource, List.of(constraint));
        assertEquals(2, initialPreparations.size(),
                "One interface source with one alternative must yield two scenarios");

        // Both scenarios have violations (Personal_Data=nonEU and Banking_Data=nonEU
        // are not affected by loan-flow routing uncertainty).
        assertTrue(
                initialPreparations.stream().noneMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Both scenarios must carry violations from the always-nonEU data stores");

        // Robust repair must remove violations in both scenarios.
        var robustOptimization = new UncertaintyAwareOptimizationManager(
                loadedModel, List.of(constraint), false);
        var robustRepaired = robustOptimization.repair();

        var afterRepair = new ScenarioPreparationService().prepare(
                robustRepaired, selectedSource, List.of(constraint));
        assertEquals(2, afterRepair.size());
        assertTrue(
                afterRepair.stream().allMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "Robust repair must be violation-free in both interface-uncertainty scenarios");
    }

    @Test
    public void robustRepairLeavesCallerInputModelUntouched() throws Exception {
        var loadedModel = loadExternal();
        var selectedSource = sourcesNamed(loadedModel, "Banking_Data_Location_Uncertain");
        var constraint = new Constraint(personalToNonEuDsl());

        var beforePreparations = new ScenarioPreparationService().prepare(
                loadedModel.baseModel(), selectedSource, List.of(constraint));
        long violatedScenariosBefore = beforePreparations.stream()
                .filter(sp -> !sp.preparation().violatingNodes().isEmpty())
                .count();
        assertTrue(violatedScenariosBefore > 0, "The base model must be violated before repair");

        var optimization = new UncertaintyAwareOptimizationManager(
                loadedModel, List.of(constraint), false, selectedSource);
        var repairedModel = optimization.repair();

        Assertions.assertNotSame(loadedModel.baseModel(), repairedModel,
                "The repaired model must be a separate artifact, not the input model");

        var afterPreparations = new ScenarioPreparationService().prepare(
                loadedModel.baseModel(), selectedSource, List.of(constraint));
        long violatedScenariosAfter = afterPreparations.stream()
                .filter(sp -> !sp.preparation().violatingNodes().isEmpty())
                .count();
        assertEquals(violatedScenariosBefore, violatedScenariosAfter,
                "The caller's input model must remain untouched by the repair");

        var repairedPreparations = new ScenarioPreparationService().prepare(
                repairedModel, selectedSource, List.of(constraint));
        assertTrue(repairedPreparations.stream().allMatch(sp -> sp.preparation().violatingNodes().isEmpty()),
                "The returned repaired model must be violation-free in every scenario");
    }

    private LoadedUncertaintyModel loadExternal() {
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                externalModelFolder().resolve("ext.dataflowdiagram"),
                externalModelFolder().resolve("ext.datadictionary"),
                externalModelFolder().resolve("ext.uncertainty")));
    }

    private static List<UncertaintySource> sourcesNamed(LoadedUncertaintyModel model, String... names) {
        List<String> wanted = List.of(names);
        return model.sources().stream()
                .filter(source -> wanted.contains(source.getEntityName()))
                .toList();
    }

    private static AnalysisConstraint personalToNonEuDsl() {
        return new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "Personal")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Location", "nonEU")
                .create();
    }

    private Path externalModelFolder() {
        return current.resolve("models")
                .resolve("DFDExternalUncertaintyMitigation");
    }

    private Path interfaceModelFolder() {
        return current.resolve("models")
                .resolve("DFDInterfaceUncertaintyMitigation");
    }

    private Constraint createDeleteNodeConstraint() {
        AnalysisConstraint dsl = new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "Personal")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Stereotype", "internal")
                .create();

        return new Constraint(dsl, List.of(
                new MitigationStrategy(List.of(new NodeLabel(new Label("Stereotype", "internal"))), 1,
                        MitigationType.DeleteNode)));
    }
}
