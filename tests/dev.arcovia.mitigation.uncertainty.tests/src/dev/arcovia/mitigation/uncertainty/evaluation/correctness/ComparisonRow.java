package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;

import java.util.List;

/**
 * Stores the complete outcome of one comparison.
 *
 * @param context shared case metadata
 * @param base    unrepaired base-model measurements
 * @param nominal nominal-repair measurements
 * @param robust  robust-repair measurements
 */
record ComparisonRow(
        CaseContext context,
        BaseResult base,
        NominalResult nominal,
        RobustResult robust
) {

    /**
     * Stores metadata shared by all arms of one comparison case.
     *
     * @param name              case identifier
     * @param cyclic            whether a prepared scenario was cyclic
     * @param selectedSourceIds selected source identifiers
     */
    record CaseContext(String name, boolean cyclic, List<String> selectedSourceIds) {
    }

    /**
     * Stores B0 measurements from the unrepaired scenario product.
     *
     * @param scenarioCount     selected-source scenario-product size
     * @param violations        unrepaired violations across the product
     * @param violatedScenarios unrepaired scenarios with violations
     * @param candidateActions  candidate actions exposed during preparation
     */
    record BaseResult(
            int scenarioCount,
            int violations,
            long violatedScenarios,
            String candidateActions
    ) {
    }

    /**
     * Stores measurements from nominal repair and full-space validation.
     *
     * @param objective         nominal repair objective, or a negative sentinel
     * @param nominalViolations residual violations in the base model
     * @param validation        independent full-space validation measurements
     * @param costBreakdown     nominal repair cost breakdown, or {@code null}
     * @param actionCount       nominal repair action count, or a negative sentinel
     * @param outcome           nominal repair outcome
     * @param errorDetail       nominal repair error class, or {@code null}
     * @param millis            nominal repair elapsed time in milliseconds
     */
    record NominalResult(
            double objective,
            int nominalViolations,
            FullSpaceMetrics validation,
            ObjectiveCostBreakdown costBreakdown,
            int actionCount,
            RepairOutcome outcome,
            String errorDetail,
            long millis
    ) {
    }

    /**
     * Stores robust-repair measurements and independent full-space validation.
     *
     * @param objective                robust repair objective, or a negative sentinel
     * @param nominalViolations        residual violations in the base model
     * @param validation               independent full-space validation measurements
     * @param consideredScenarioCount  scenarios considered by the robust solver
     * @param costBreakdown            robust repair cost breakdown, or {@code null}
     * @param actionCount              robust repair action count, or a negative sentinel
     * @param solverStatus             robust solver status
     * @param pipelineValidationStatus robust pipeline validation status
     * @param outcome                  independently validated of a robust outcome
     * @param errorDetail              robust repair error class, or {@code null}
     * @param millis                   robust repair elapsed time in milliseconds
     * @param selectedActions          selected robust actions for diagnostics
     */
    record RobustResult(
            double objective,
            int nominalViolations,
            FullSpaceMetrics validation,
            int consideredScenarioCount,
            ObjectiveCostBreakdown costBreakdown,
            int actionCount,
            String solverStatus,
            String pipelineValidationStatus,
            RepairOutcome outcome,
            String errorDetail,
            long millis,
            String selectedActions
    ) {
    }

    /**
     * Classifies the relative correctness of the nominal and robust repair arms.
     *
     * @return the comparison verdict
     */
    ComparisonVerdict verdict() {
        if (robust.outcome() != RepairOutcome.VALID_REPAIR) {
            return ComparisonVerdict.robustUnavailable(robust.outcome());
        } else if (nominal.validation().totalViolations() < 0) {
            return ComparisonVerdict.nominalUnavailable(nominal.outcome());
        } else if (nominal.nominalViolations() > 0) {
            return ComparisonVerdict.NOMINAL_DEFECT;
        } else if (nominal.validation().totalViolations() > 0) {
            return ComparisonVerdict.NOMINAL_INSUFFICIENT;
        }
        return ComparisonVerdict.NOMINAL_SUFFICIENT;
    }

    /**
     * Calculates the fraction of scenarios where the nominal repair transfers correctly.
     *
     * @return the transfer rate, or {@code -1} when the nominal repair is unavailable
     */
    double transferRate() {
        if (nominal.validation().totalViolations() < 0 || base.scenarioCount() <= 0) {
            return -1;
        }

        return (base.scenarioCount() - nominal.validation().violatedScenarios())
               / (double) base.scenarioCount();
    }

    /**
     * Calculates the robust goal divided by the nominal goal.
     *
     * @return the price of robustness, or {@link Double#NaN} when it is undefined
     */
    double priceOfRobustness() {
        if (robust.objective() < 0 || nominal.objective() <= 0) {
            return Double.NaN;
        }

        return robust.objective() / nominal.objective();
    }

    /**
     * Calculates the fraction of scenarios where the nominal repair leaves a violation.
     *
     * @return the robustness utility, or {@link Double#NaN} when the scenario space is unknown
     */
    double robustnessUtility() {
        if (base.scenarioCount() <= 0) {
            return Double.NaN;
        }

        return (double) nominal.validation().violatedScenarios() / base.scenarioCount();
    }
}
