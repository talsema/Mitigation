package dev.arcovia.mitigation.uncertainty.enumeration;

import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioCombinationGeneratorTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    private Path testModels() {
        return current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
    }

    @Test
    public void singleSourceProducesDefaultAndAlternativeSelections() {
        var loadedModel = loadExternalUncertaintyModel();
        var source = loadedModel.sources().stream()
                .filter(uncertaintySource -> uncertaintySource.getEntityName().equals("User_Location_Uncertain"))
                .findFirst()
                .orElseThrow();

        var combinations = new ScenarioCombinationGenerator().generate(List.of(source));

        assertEquals(2, combinations.size());
        assertTrue(combinations.stream().anyMatch(combination -> combination.size() == 1 && combination.get(0).scenario().isEmpty()));
        assertTrue(combinations.stream().anyMatch(combination -> combination.size() == 1 && combination.get(0).scenario().isPresent()));
    }

    @Test
    public void noSourcesProduceOneEmptyCombination() {
        var combinations = new ScenarioCombinationGenerator().generate(List.of());
        assertEquals(List.of(List.of()), combinations);
    }

    @Test
    public void twoSourcesProduceCartesianProduct() {
        var loadedModel = loadExternalUncertaintyModel();
        var selectedSources = loadedModel.sources().stream()
                .filter(uncertaintySource ->
                        uncertaintySource.getEntityName().equals("User_Location_Uncertain")
                        || uncertaintySource.getEntityName().equals("Developer_Level_Uncertain")
                )
                .toList();

        var combinations = new ScenarioCombinationGenerator().generate(selectedSources);

        assertEquals(4, combinations.size());
        assertTrue(combinations.stream().allMatch(combination -> combination.size() == 2));
    }

    @Test
    public void twoSourcesProduceStableScenarioSelectionOrder() {
        var loadedModel = loadExternalUncertaintyModel();
        var selectedSources = loadedModel.sources().stream()
                .filter(uncertaintySource ->
                        uncertaintySource.getEntityName().equals("User_Location_Uncertain")
                        || uncertaintySource.getEntityName().equals("Developer_Level_Uncertain")
                )
                .toList();

        var combinations = new ScenarioCombinationGenerator().generate(selectedSources);
        var signatures = combinations.stream()
                .map(this::signature)
                .toList();

        assertEquals(List.of(
                        List.of("User_Location_Uncertain=<default>", "Developer_Level_Uncertain=<default>"),
                        List.of("User_Location_Uncertain=<default>", "Developer_Level_Uncertain=<alternative>"),
                        List.of("User_Location_Uncertain=<alternative>", "Developer_Level_Uncertain=<default>"),
                        List.of("User_Location_Uncertain=<alternative>", "Developer_Level_Uncertain=<alternative>")),
                signatures);
    }

    @Test
    public void enumeratesLargeProduct() {
        var loadedModel = loadExternalUncertaintyModel();
        var binarySource = loadedModel.sources().stream()
                .filter(uncertaintySource -> uncertaintySource.getEntityName().equals("User_Location_Uncertain"))
                .findFirst()
                .orElseThrow();

        var combinations = new ScenarioCombinationGenerator().generate(Collections.nCopies(14, binarySource));

        assertEquals(1 << 14, combinations.size(), "combinations must be enumerated, not refused");
    }

    private List<String> signature(List<ScenarioSelection> combination) {
        return combination.stream()
                .map(selection -> selection.source().getEntityName() + "="
                                  + selection.scenario().map(scenario -> "<alternative>").orElse("<default>"))
                .toList();
    }

    private LoadedUncertaintyModel loadExternalUncertaintyModel() {
        var modelFolder = testModels();
        var spec = new UncertaintyModelSpec(
                modelFolder.resolve("ext.dataflowdiagram"),
                modelFolder.resolve("ext.datadictionary"),
                modelFolder.resolve("ext.uncertainty"));

        return new UncertaintyModelLoader().load(spec);
    }
}
