package dev.arcovia.mitigation.uncertainty.verification.materialization;

import dev.arcovia.mitigation.uncertainty.materialization.MaterializedScenario;
import dev.arcovia.mitigation.uncertainty.materialization.ScenarioMaterializer;

import dev.abunai.confidentiality.analysis.core.UncertaintyUtils;
import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioSelection;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.junit.jupiter.api.Test;
import tools.mdsd.modelingfoundations.identifier.NamedElement;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioMaterializerTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    private Path testModels() {
        return current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
    }

    @Test
    public void materializesUserLocationScenarioIntoConcreteDfd() {
        var modelFolder = testModels();
        var spec = new UncertaintyModelSpec(
                modelFolder.resolve("ext.dataflowdiagram"),
                modelFolder.resolve("ext.datadictionary"),
                modelFolder.resolve("ext.uncertainty"));

        var loadedModel = new UncertaintyModelLoader().load(spec);
        var source = loadedModel.sources().stream()
                .filter(uncertaintySource -> uncertaintySource.getEntityName().equals("User_Location_Uncertain"))
                .findFirst()
                .orElseThrow();
        var scenario = UncertaintyUtils.getUncertaintyScenarios(source).stream()
                .filter(candidate -> !UncertaintyUtils.isDefaultScenario(source, candidate))
                .findFirst()
                .orElseThrow();

        var materializedScenario = new ScenarioMaterializer().materialize(
                loadedModel.baseModel(),
                List.of(new ScenarioSelection(source, Optional.of(scenario))));

        var userLabels = getNodeLabels(materializedScenario);

        assertTrue(userLabels.contains("nonEU"));
        assertFalse(userLabels.contains("EU"));
    }

    @Test
    void defaultSelectionRetainsTheUnmodifiedBaseModel() {
        var modelFolder = testModels();
        var loadedModel = new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                modelFolder.resolve("ext.dataflowdiagram"),
                modelFolder.resolve("ext.datadictionary"),
                modelFolder.resolve("ext.uncertainty")));
        var source = loadedModel.sources().stream()
                .filter(candidate -> candidate.getEntityName().equals("User_Location_Uncertain"))
                .findFirst()
                .orElseThrow();

        var defaultScenario = new ScenarioMaterializer().materialize(
                loadedModel.baseModel(), List.of(new ScenarioSelection(source, Optional.empty())));

        assertSame(defaultScenario.model(), loadedModel.baseModel());
        assertTrue(getNodeLabels(defaultScenario).contains("EU"));
    }

    private List<String> getNodeLabels(MaterializedScenario materializedScenario) {
        return materializedScenario.model().dataFlowDiagram().getNodes().stream()
                .filter(node -> node.getEntityName().equals("User"))
                .findFirst()
                .orElseThrow()
                .getProperties().stream()
                .map(NamedElement::getEntityName)
                .toList();
    }
}
