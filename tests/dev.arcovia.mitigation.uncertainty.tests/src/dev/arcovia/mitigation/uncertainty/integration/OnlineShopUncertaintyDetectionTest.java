package dev.arcovia.mitigation.uncertainty.integration;

import dev.abunai.confidentiality.analysis.core.UncertaintyUtils;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintyScenario;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDExternalUncertaintyScenario;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDExternalUncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDInterfaceUncertaintyScenario;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDInterfaceUncertaintySource;
import dev.arcovia.mitigation.uncertainty.enumeration.ScenarioCombinationGenerator;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import dev.arcovia.mitigation.uncertainty.materialization.MaterializedScenario;
import dev.arcovia.mitigation.uncertainty.materialization.ScenarioMaterializer;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.Label;
import org.dataflowanalysis.dfd.datadictionary.LabelType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OnlineShopUncertaintyDetectionTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void detectsAllUncertaintiesInOnlineShopDfdRunningExample() {
        var loadedModel = loadOnlineShopModel();
        var sources = loadedModel.sources();

        assertEquals(2, sources.size(), "The full online-shop DFD fixture should declare two uncertainty sources");
        assertEquals(1, countSourcesOfType(sources, DFDExternalUncertaintySource.class));
        assertEquals(1, countSourcesOfType(sources, DFDInterfaceUncertaintySource.class));

        var externalSource = onlySourceOfType(sources, DFDExternalUncertaintySource.class);
        assertEquals("Database", externalSource.getTarget().getEntityName());
        assertEquals(List.of("nonEU"), labelNames(externalSource.getTargetProperties()));
        assertOneDefaultAndOneAlternativeScenario(externalSource);

        var externalAlternative = onlyNonDefaultScenario(externalSource, DFDExternalUncertaintyScenario.class);
        assertEquals(List.of("EU"), labelNames(externalAlternative.getTargetProperties()));

        var interfaceSource = onlySourceOfType(sources, DFDInterfaceUncertaintySource.class);
        assertEquals("dataToDB", interfaceSource.getTargetFlow().getEntityName());
        assertEquals("Database", interfaceSource.getTargetFlow().getDestinationNode().getEntityName());
        assertOneDefaultAndOneAlternativeScenario(interfaceSource);

        var interfaceAlternative = onlyNonDefaultScenario(interfaceSource, DFDInterfaceUncertaintyScenario.class);
        assertEquals("EUDatabase", interfaceAlternative.getTargetNode().getEntityName());
        assertEquals("EUDB_in", interfaceAlternative.getTargetInPin().getEntityName());

        resolveDetectedOnlineShopUncertainties(loadedModel, sources);
    }

    private LoadedUncertaintyModel loadOnlineShopModel() {
        var modelFolder = onlineShopModelFolder();
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                modelFolder.resolve("onlineshop.dataflowdiagram"),
                modelFolder.resolve("onlineshop.datadictionary"),
                modelFolder.resolve("onlineshop.uncertainty")));
    }

    private Path onlineShopModelFolder() {
        return current
                .resolve("models")
                .resolve("UncertainOnlineShopDFD");
    }

    private <T extends UncertaintySource> long countSourcesOfType(List<UncertaintySource> sources, Class<T> type) {
        return sources.stream().filter(type::isInstance).count();
    }

    private <T extends UncertaintySource> T onlySourceOfType(List<UncertaintySource> sources, Class<T> type) {
        return sources.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private void assertOneDefaultAndOneAlternativeScenario(UncertaintySource source) {
        var scenarios = UncertaintyUtils.getUncertaintyScenarios(source);

        assertEquals(2, scenarios.size());
        assertEquals(1, scenarios.stream()
                .filter(scenario -> UncertaintyUtils.isDefaultScenario(source, scenario))
                .count());
        assertEquals(1, scenarios.stream()
                .filter(scenario -> !UncertaintyUtils.isDefaultScenario(source, scenario))
                .count());
    }

    private <T extends UncertaintyScenario> T onlyNonDefaultScenario(UncertaintySource source, Class<T> type) {
        return UncertaintyUtils.getUncertaintyScenarios(source).stream()
                .filter(scenario -> !UncertaintyUtils.isDefaultScenario(source, scenario))
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private List<String> labelNames(List<Label> labels) {
        return labels.stream().map(Label::getEntityName).toList();
    }

    private void resolveDetectedOnlineShopUncertainties(LoadedUncertaintyModel loadedModel,
                                                        List<UncertaintySource> detectedSources) {
        assertNotNull(loadedModel.baseModel(), "Resolving will need the base DFD model");

        var combinations = new ScenarioCombinationGenerator().generate(detectedSources);
        assertEquals(4, combinations.size(), "Two binary uncertainty sources should resolve to 2 x 2 concrete scenarios");
        assertTrue(combinations.stream().allMatch(combination -> combination.size() == detectedSources.size()));
        assertTrue(combinations.stream().anyMatch(combination -> combination.stream()
                .allMatch(selection -> selection.scenario().isEmpty())));
        assertTrue(combinations.stream().anyMatch(combination -> combination.stream()
                .allMatch(selection -> selection.scenario().isPresent())));

        var materializer = new ScenarioMaterializer();
        var materializedScenarios = combinations.stream()
                .map(combination -> materializer.materialize(loadedModel.baseModel(), combination))
                .toList();

        assertEquals(4, materializedScenarios.size());
        assertResolvedVariantExists(materializedScenarios, "nonEU", "Database");
        assertResolvedVariantExists(materializedScenarios, "EU", "Database");
        assertResolvedVariantExists(materializedScenarios, "nonEU", "EUDatabase");
        assertResolvedVariantExists(materializedScenarios, "EU", "EUDatabase");
    }

    private void assertResolvedVariantExists(List<MaterializedScenario> scenarios, String databaseLocation,
                                             String dataToDbDestination) {
        assertTrue(scenarios.stream().anyMatch(scenario -> hasDatabaseLocation(scenario.model(), databaseLocation)
                                                           && hasDataToDbDestination(scenario.model(), dataToDbDestination)),
                "Expected resolved online-shop scenario with Database Location." + databaseLocation
                + " and dataToDB -> " + dataToDbDestination);
    }

    private boolean hasDatabaseLocation(DataFlowDiagramAndDictionary model, String location) {
        return getNodeLabelNames(model).equals(List.of(location));
    }

    private boolean hasDataToDbDestination(DataFlowDiagramAndDictionary model, String destinationNodeName) {
        return model.dataFlowDiagram().getFlows().stream()
                .filter(flow -> flow.getEntityName().equals("dataToDB"))
                .anyMatch(flow -> flow.getDestinationNode().getEntityName().equals(destinationNodeName));
    }

    private List<String> getNodeLabelNames(DataFlowDiagramAndDictionary model) {
        return model.dataFlowDiagram().getNodes().stream()
                .filter(node -> node.getEntityName().equals("Database"))
                .findFirst()
                .orElseThrow()
                .getProperties().stream()
                .filter(label -> ((LabelType) label.eContainer()).getEntityName().equals("Location"))
                .map(Label::getEntityName)
                .toList();
    }
}
