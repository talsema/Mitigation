package dev.arcovia.mitigation.uncertainty.loading;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UncertaintyModelLoaderTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void loadsValidatedExternalUncertaintyModel() {
        var modelFolder = current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
        var spec = new UncertaintyModelSpec(
                modelFolder.resolve("ext.dataflowdiagram"),
                modelFolder.resolve("ext.datadictionary"),
                modelFolder.resolve("ext.uncertainty"));

        var loadedModel = new UncertaintyModelLoader().load(spec);

        assertNotNull(loadedModel.baseModel());
        assertNotNull(loadedModel.baseModel().dataFlowDiagram());
        assertNotNull(loadedModel.baseModel().dataDictionary());
        assertEquals(6, loadedModel.sources().size());
        assertTrue(loadedModel.sources().stream()
                .anyMatch(source -> source.getEntityName().equals("User_Location_Uncertain")));
    }
}
