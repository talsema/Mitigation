package dev.arcovia.mitigation.uncertainty.pruning;

import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.junit.jupiter.api.Test;
import tools.mdsd.modelingfoundations.identifier.NamedElement;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SourceImpactFilterTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void dropsSourceWhoseTargetNodeIsOnNoTransposeFlowGraph() {
        LoadedUncertaintyModel loaded = loadExternalModel();
        var kept = new SourceImpactFilter().retainImpactful(loaded.baseModel(), loaded.sources());

        var keptNames = kept.stream().map(NamedElement::getEntityName).toList();
        assertFalse(keptNames.contains("Settings_DB_Location_Uncertain"),
                "Settings_DB is on no transpose flow graph, so its source cannot affect any constraint and must be pruned");
        assertTrue(keptNames.contains("Banking_Data_Location_Uncertain"),
                "Banking_DB lies on a constraint-carrying flow, so its source must be retained");
        assertEquals(loaded.sources().size() - 1, kept.size(),
                "exactly the one off-transpose-flow-graph source is dropped");
    }

    @Test
    public void retainedSetIsOrderPreservingAndStableUnderAddingTheIrrelevantSource() {
        LoadedUncertaintyModel loaded = loadExternalModel();
        var filter = new SourceImpactFilter();

        var keptFromAll = filter.retainImpactful(loaded.baseModel(), loaded.sources());
        assertEquals(loaded.sources().stream().filter(keptFromAll::contains).toList(), keptFromAll,
                "retained sources must be an order-preserving subset of the input");

        var withoutIrrelevant = loaded.sources().stream()
                .filter(source -> !source.getEntityName().equals("Settings_DB_Location_Uncertain"))
                .toList();
        assertEquals(keptFromAll, filter.retainImpactful(loaded.baseModel(), withoutIrrelevant),
                "adding or removing the provably-irrelevant source leaves the impact-relevant set unchanged");
    }

    private LoadedUncertaintyModel loadExternalModel() {
        var folder = current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                folder.resolve("ext.dataflowdiagram"),
                folder.resolve("ext.datadictionary"),
                folder.resolve("ext.uncertainty")));
    }
}
