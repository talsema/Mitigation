package dev.arcovia.mitigation.uncertainty.verification.pruning;

import dev.arcovia.mitigation.uncertainty.pruning.SourceImpactFilter;
import dev.arcovia.mitigation.uncertainty.pruning.SourcePartitioner;
import dev.arcovia.mitigation.uncertainty.pruning.TopologyEffect;

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

    @Test
    public void doesNotDecomposeWhenASourceCanRewireTheDiagram() {
        LoadedUncertaintyModel loaded = loadMixedTypeModel();
        var sources = loaded.sources();

        assertTrue(TopologyEffect.anyChangesTopology(sources),
                "fixture precondition: this model must declare a component, interface, or connector source");

        var clusters = new SourcePartitioner().partition(loaded.baseModel(), sources);

        assertEquals(1, clusters.size(),
                "decomposition rests on base-model flow graphs; a source that rewires the diagram can make "
                + "two sources interact that share no base-model graph, so the space must not be split");
        assertEquals(sources.size(), clusters.get(0).size(), "the single cluster keeps every source");
    }

    @Test
    public void retainsEverySourceWhenASourceCanRewireTheDiagram() {
        LoadedUncertaintyModel loaded = loadMixedTypeModel();

        var retained = new SourceImpactFilter().retainImpactful(loaded.baseModel(), loaded.sources());

        assertEquals(loaded.sources().size(), retained.size(),
                "impact pruning judges a target against base-model flow graphs; a rewiring source can make an "
                + "off-graph node reachable, so no source may be dropped");
    }

    private LoadedUncertaintyModel loadMixedTypeModel() {
        var folder = current.resolve("models").resolve("mitigation_example");
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                folder.resolve("mitigation_example.dataflowdiagram"),
                folder.resolve("mitigation_example.datadictionary"),
                folder.resolve("mitigation_example.uncertainty")));
    }

    private LoadedUncertaintyModel loadExternalModel() {
        var folder = current.resolve("models").resolve("DFDExternalUncertaintyMitigation");
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                folder.resolve("ext.dataflowdiagram"),
                folder.resolve("ext.datadictionary"),
                folder.resolve("ext.uncertainty")));
    }
}
