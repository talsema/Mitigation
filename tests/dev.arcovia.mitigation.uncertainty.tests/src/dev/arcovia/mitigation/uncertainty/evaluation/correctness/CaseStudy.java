package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

import dev.abunai.confidentiality.analysis.model.uncertainty.UncertaintySource;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import org.dataflowanalysis.analysis.dsl.AnalysisConstraint;

import java.util.function.Predicate;

/**
 * Defines one reproducible evaluation case together with the outcome it is expected to produce.
 *
 * @param name         case identifier
 * @param spec         the model files this case loads
 * @param constraint   the confidentiality predicate this case evaluates
 * @param sourceFilter selected uncertainty-source predicate, or {@code null} for every source
 * @param expectation  the frozen outcome this case must reproduce
 */
record CaseStudy(
        String name,
        UncertaintyModelSpec spec,
        AnalysisConstraint constraint,
        Predicate<UncertaintySource> sourceFilter,
        CaseExpectation expectation) {
}
