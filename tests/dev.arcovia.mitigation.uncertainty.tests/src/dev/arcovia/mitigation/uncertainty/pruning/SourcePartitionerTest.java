package dev.arcovia.mitigation.uncertainty.pruning;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourcePartitionerTest {
    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void keepsAConnectedSubsystemAsOneCluster() {
        LoadedUncertaintyModel loaded = loadExternalModel();
        var relevant = new SourceImpactFilter().retainImpactful(loaded.baseModel(), loaded.sources());

        var clusters = new SourcePartitioner().partition(loaded.baseModel(), relevant);

        assertEquals(1, clusters.size(),
                "the connected banking subsystem must not be split, or per-cluster solving would be unsound");
        assertEquals(relevant, clusters.get(0), "the single cluster must contain every relevant source, in order");
    }

    @Test
    public void partitionIsAnOrderPreservingExactCoverOfTheInput() {
        LoadedUncertaintyModel loaded = loadExternalModel();
        var relevant = new SourceImpactFilter().retainImpactful(loaded.baseModel(), loaded.sources());

        var clusters = new SourcePartitioner().partition(loaded.baseModel(), relevant);

        List<UncertaintySource> flattened = clusters.stream().flatMap(List::stream).toList();
        assertEquals(relevant.size(), flattened.size(), "every source appears in exactly one cluster");
        assertTrue(flattened.containsAll(relevant) && relevant.containsAll(flattened),
                "the clusters must cover exactly the input source set");
    }

    @Test
    public void emptyInputYieldsNoClusters() {
        LoadedUncertaintyModel loaded = loadExternalModel();
        assertEquals(List.of(), new SourcePartitioner().partition(loaded.baseModel(), List.of()));
    }

    private LoadedUncertaintyModel loadExternalModel() {
        var folder = current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                folder.resolve("ext.dataflowdiagram"),
                folder.resolve("ext.datadictionary"),
                folder.resolve("ext.uncertainty")));
    }
}
