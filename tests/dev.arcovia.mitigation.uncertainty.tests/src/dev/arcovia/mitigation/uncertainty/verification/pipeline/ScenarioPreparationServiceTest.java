package dev.arcovia.mitigation.uncertainty.verification.pipeline;

import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparationService;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.dataflowanalysis.analysis.dsl.constraint.ConstraintDSL;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPreparationServiceTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void preparesEachScenarioCombinationForTheSelectedSource() {
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                externalModelFolder().resolve("ext.dataflowdiagram"),
                externalModelFolder().resolve("ext.datadictionary"),
                externalModelFolder().resolve("ext.uncertainty")));

        var selectedSource = loadedModel.sources().stream()
                .filter(source -> source.getEntityName().equals("User_Location_Uncertain"))
                .toList();

        var preparations = new ScenarioPreparationService().prepare(loadedModel, selectedSource, List.of(createNonEuConstraint()));

        assertEquals(2, preparations.size());
        assertTrue(preparations.stream().map(preparation -> preparation.scenario().id())
                .anyMatch(id -> id.endsWith(":default")));
        assertTrue(preparations.stream().map(preparation -> preparation.scenario().id())
                .anyMatch(id -> !id.endsWith(":default")));
        assertTrue(preparations.stream()
                .allMatch(preparation -> preparation.preparation().preparedConstraints().size() == 1));
        assertTrue(preparations.stream()
                .anyMatch(preparation -> !preparation.preparation().violatingNodes().isEmpty()));
        assertTrue(preparations.stream()
                .anyMatch(preparation -> !preparation.preparation().mitigations().isEmpty()));
    }

    @Test
    public void fallsBackToOneBaseScenarioWhenNoSourcesAreSelected() {
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                externalModelFolder().resolve("ext.dataflowdiagram"),
                externalModelFolder().resolve("ext.datadictionary"),
                externalModelFolder().resolve("ext.uncertainty")));

        var preparations = new ScenarioPreparationService().prepare(loadedModel, List.of(), List.of(createNonEuConstraint()));

        assertEquals(1, preparations.size());
        assertEquals("base", preparations.get(0).scenario().id());
        assertFalse(preparations.get(0).preparation().preparedConstraints().isEmpty());
    }

    @Test
    public void materialisesInterfaceUncertaintyScenarioByRedirectingFlowDestination() {
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                interfaceModelFolder().resolve("int.dataflowdiagram"),
                interfaceModelFolder().resolve("int.datadictionary"),
                interfaceModelFolder().resolve("int.uncertainty")));

        var selectedSource = loadedModel.sources().stream()
                .filter(source -> source.getEntityName().equals("system_state_uncertain"))
                .toList();

        var preparations = new ScenarioPreparationService().prepare(
                loadedModel, selectedSource, List.of(createNonEuConstraint()));

        assertEquals(2, preparations.size(),
                "One interface source with one alternative must yield exactly two scenarios");

        var defaultScenario = preparations.stream()
                .filter(sp -> sp.scenario().id().endsWith(":default"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a ':default' scenario"));
        var altScenario = preparations.stream()
                .filter(sp -> !sp.scenario().id().endsWith(":default"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a non-default scenario"));

        var bsFlowDefault = defaultScenario.scenario().model().dataFlowDiagram().getFlows().stream()
                .filter(f -> f.getEntityName().equals("b_s"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("b_s flow missing from default scenario model"));
        var bsFlowAlt = altScenario.scenario().model().dataFlowDiagram().getFlows().stream()
                .filter(f -> f.getEntityName().equals("b_s"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("b_s flow missing from alt scenario model"));

        assertEquals("show_system_state2", bsFlowDefault.getDestinationNode().getEntityName(),
                "Default scenario must retain the base-model flow destination (show_system_state2)");
        assertEquals("show_system_state", bsFlowAlt.getDestinationNode().getEntityName(),
                "Alt scenario must redirect b_s to show_system_state via chooseInterfaceScenario");
    }

    @Test
    public void parallelPreparationIsDeterministicInOrderAndViolations() {
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                externalModelFolder().resolve("ext.dataflowdiagram"),
                externalModelFolder().resolve("ext.datadictionary"),
                externalModelFolder().resolve("ext.uncertainty")));

        var selectedSources = loadedModel.sources().stream()
                .filter(source -> source.getEntityName().equals("User_Location_Uncertain")
                                  || source.getEntityName().equals("Banking_Data_Location_Uncertain"))
                .toList();
        assertEquals(2, selectedSources.size(), "fixture must expose both banking External sources");

        var service = new ScenarioPreparationService();
        List<String> referenceIds = null;
        List<Integer> referenceViolationCounts = null;
        for (int run = 0; run < 5; run++) {
            var preparations = service.prepare(loadedModel, selectedSources, List.of(createNonEuConstraint()));

            assertEquals(4, preparations.size(), "two binary sources must yield four scenarios");
            var ids = preparations.stream().map(preparation -> preparation.scenario().id()).toList();
            var violationCounts = preparations.stream()
                    .map(preparation -> preparation.preparation().violatingNodes().size())
                    .toList();

            if (referenceIds == null) {
                referenceIds = ids;
                referenceViolationCounts = violationCounts;
            } else {
                assertEquals(referenceIds, ids, "scenario-id ordering must be identical across parallel runs");
                assertEquals(referenceViolationCounts, violationCounts,
                        "per-scenario violation counts must be identical across parallel runs");
            }
        }
    }

    private Path externalModelFolder() {
        return current.resolve("models")
                .resolve("DFDExternalUncertaintyMitigation");
    }

    private Path interfaceModelFolder() {
        return current.resolve("models")
                .resolve("DFDInterfaceUncertaintyMitigation");
    }

    private Constraint createNonEuConstraint() {
        return new Constraint(new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "Personal")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Location", "nonEU")
                .create());
    }
}
