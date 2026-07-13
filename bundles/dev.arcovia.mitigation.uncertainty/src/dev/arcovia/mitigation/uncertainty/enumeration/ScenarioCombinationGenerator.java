package dev.arcovia.mitigation.uncertainty.enumeration;

import dev.abunai.confidentiality.analysis.core.UncertaintyUtils;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import org.eclipse.jdt.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Lists the scenario-space Cartesian product. Pipeline stage 2.
 */
public final class ScenarioCombinationGenerator {

    /**
     * Returns every default-or-alternative selection for the given sources.
     *
     * @param sources the sources to enumerate
     * @return the deterministic Cartesian product of source selections
     */
    public List<List<ScenarioSelection>> generate(@NonNull List<UncertaintySource> sources) {
        Objects.requireNonNull(sources, "sources must not be null");

        return generateCombinations(List.copyOf(sources));
    }

    /**
     * Builds the Cartesian product for the remaining sources.
     *
     * @param sources the sources being enumerated
     * @return every selection combination for {@code sources}
     */
    private List<List<ScenarioSelection>> generateCombinations(List<UncertaintySource> sources) {
        if (sources.isEmpty()) {
            return List.of(List.of());
        }

        UncertaintySource source = sources.get(0);
        var remainingCombinations = generateCombinations(sources.subList(1, sources.size()));
        return selectionsFor(source)
                .flatMap(selection -> remainingCombinations.stream()
                        .map(combination -> Stream.concat(Stream.of(selection), combination.stream()).toList()))
                .toList();
    }

    /**
     * Streams the default selection before the source's alternatives.
     *
     * @param source the source whose choices are enumerated
     * @return the default selection followed by alternative selections
     */
    private Stream<ScenarioSelection> selectionsFor(UncertaintySource source) {
        return Stream.concat(
                Stream.of(new ScenarioSelection(source, Optional.empty())),
                UncertaintyUtils.getUncertaintyScenarios(source).stream()
                        .filter(scenario -> !UncertaintyUtils.isDefaultScenario(source, scenario))
                        .map(scenario -> new ScenarioSelection(source, Optional.of(scenario))));
    }
}
