package dev.arcovia.mitigation.uncertainty.enumeration;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintyScenario;
import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import org.eclipse.jdt.annotation.NonNull;

import java.util.Objects;
import java.util.Optional;

/**
 * One source's choice within a scenario combination.
 *
 * @param source   the uncertainty source being resolved
 * @param scenario the chosen alternative scenario, or empty for the source's default branch
 */
public record ScenarioSelection(@NonNull UncertaintySource source, @NonNull Optional<UncertaintyScenario> scenario) {

    /**
     * Validates one source choice in a scenario combination.
     *
     * @param source   the source being resolved
     * @param scenario the selected alternative, or empty for the default branch
     */
    public ScenarioSelection {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(scenario, "scenario must not be null");
    }
}
