package dev.arcovia.mitigation.uncertainty.pruning;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDComponentUncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDConnectorUncertaintySource;
import dev.abunai.confidentiality.analysis.model.uncertainty.dfd.DFDInterfaceUncertaintySource;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * Decides whether an uncertainty source can change the structure of the data flow diagram.
 * <p>
 * Both scenario-space reductions judge a source against the transpose flow graphs of the
 * base model. That is only valid while materialization leaves the graph structure
 * alone. Component, interface, and connector sources do not: materializing them rewires flow
 * endpoints and can remove a node, so a node that lies on no base-model flow graph can become
 * reachable in an alternative scenario. Judging any source against the base-model structure is
 * therefore unsound as soon as one such source is selected.
 *
 * @see SourceImpactFilter
 * @see SourcePartitioner
 */
public final class TopologyEffect {

    private TopologyEffect() {
    }

    /**
     * Reports whether materializing this source can change the diagram's structure.
     *
     * @param source the source to classify
     * @return {@code true} for component, interface, and connector sources, which rewire flow
     * endpoints or replace nodes; {@code false} for external and behavior sources,
     * which only change labels and assignments
     */
    public static boolean changesTopology(@NonNull UncertaintySource source) {
        Objects.requireNonNull(source, "source must not be null");
        return source instanceof DFDComponentUncertaintySource
               || source instanceof DFDInterfaceUncertaintySource
               || source instanceof DFDConnectorUncertaintySource;
    }

    /**
     * Reports whether any selected source can change the diagram's structure.
     *
     * @param sources the selected sources
     * @return {@code true} if at least one source rewires the graph
     */
    public static boolean anyChangesTopology(@NonNull List<UncertaintySource> sources) {
        Objects.requireNonNull(sources, "sources must not be null");
        return sources.stream().anyMatch(TopologyEffect::changesTopology);
    }
}
