package dev.arcovia.mitigation.uncertainty.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One measurement row shared by every evaluation runner.
 */
public record EvaluationRecord(
        @JsonProperty("case") String caseName,
        String constraintSet,
        String arm,
        String costProfile,
        int sourcesDeclared,
        int sourcesAfterPruning,
        long scenariosFull,
        long scenariosConsidered,
        long scenariosValidated,
        int violationsBefore,
        int violationsAfter,
        int violationsInOwnModel,
        long scenariosWithViolation,
        String cyclic,
        int actionsSelected,
        double objective,
        String solverStatus,
        double msLoad,
        double msPrepare,
        double msSolve,
        double msApply,
        double msValidate,
        double msTotal,
        int ilpVariables,
        int ilpCoverageRows,
        int ilpContradictionRows,
        int repetition,
        String stopReason) {

    /**
     * Marks an integral field the producing runner does not measure.
     */
    public static final int UNSET_COUNT = -1;

    /**
     * Marks a floating-point field the producing runner does not measure.
     */
    public static final double UNSET_VALUE = Double.NaN;

    /**
     * The column order of {@code measurements.csv}, and the order {@link #toCsvRow()} emits.
     */
    public static final List<String> COLUMNS = RecordCsv.columns(EvaluationRecord.class);

    /**
     * Validates the identifying fields, which every runner can supply.
     */
    public EvaluationRecord {
        Objects.requireNonNull(caseName, "caseName must not be null");
        Objects.requireNonNull(arm, "arm must not be null");
        constraintSet = constraintSet == null ? "" : constraintSet;
        costProfile = costProfile == null ? "" : costProfile;
        solverStatus = solverStatus == null ? "" : solverStatus;
        stopReason = stopReason == null ? "" : stopReason;
        cyclic = cyclic == null ? "" : cyclic;
    }

    /**
     * Starts a record for one case and arm, with every measured field unset.
     *
     * @param caseName the case or model identifier
     * @param arm      the compared configuration, for example {@code B0}, {@code B1}, or {@code R}
     * @return a builder carrying only the identifying fields
     */
    public static Builder of(String caseName, String arm) {
        return new Builder(caseName, arm);
    }

    /**
     * Renders this record in {@link #COLUMNS} order.
     *
     * @return one CSV line without a trailing newline
     * @throws IllegalStateException if a component cannot be read
     */
    public String toCsvRow() {
        return Arrays.stream(EvaluationRecord.class.getRecordComponents())
                .map(this::cell)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * Renders one part as a CSV cell, leaving an unmeasured field empty.
     *
     * @param component the record component to read
     * @return the cell text
     * @throws IllegalStateException if the component cannot be read
     */
    private String cell(RecordComponent component) {
        Object value;
        try {
            value = component.getAccessor().invoke(this);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not read " + component.getName(), exception);
        }
        if (value instanceof Double number) {
            return value(number);
        }
        if (value instanceof Number number) {
            return count(number.longValue());
        }
        return quote((String) value);
    }

    private static String count(long field) {
        return field == UNSET_COUNT ? "" : Long.toString(field);
    }

    /**
     * Renders a measured value for a CSV cell.
     */
    private static String value(double field) {
        return Double.isNaN(field) ? "" : String.format(Locale.ROOT, "%.3f", field);
    }

    private static String quote(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"").replace("\n", " ") + '"';
    }

    /**
     * Collects the fields one runner can measure, leaving the rest unset.
     */
    public static final class Builder {

        private final String caseName;
        private final String arm;
        private String constraintSet = "";
        private String costProfile = "";
        private int sourcesDeclared = UNSET_COUNT;
        private int sourcesAfterPruning = UNSET_COUNT;
        private long scenariosFull = UNSET_COUNT;
        private long scenariosConsidered = UNSET_COUNT;
        private long scenariosValidated = UNSET_COUNT;
        private int violationsBefore = UNSET_COUNT;
        private int violationsAfter = UNSET_COUNT;
        private int violationsInOwnModel = UNSET_COUNT;
        private long scenariosWithViolation = UNSET_COUNT;
        private String cyclic = "";
        private int actionsSelected = UNSET_COUNT;
        private double objective = UNSET_VALUE;
        private String solverStatus = "";
        private double msLoad = UNSET_VALUE;
        private double msPrepare = UNSET_VALUE;
        private double msSolve = UNSET_VALUE;
        private double msApply = UNSET_VALUE;
        private double msValidate = UNSET_VALUE;
        private double msTotal = UNSET_VALUE;
        private int ilpVariables = UNSET_COUNT;
        private int ilpCoverageRows = UNSET_COUNT;
        private int ilpContradictionRows = UNSET_COUNT;
        private int repetition = UNSET_COUNT;
        private String stopReason = "";

        private Builder(String caseName, String arm) {
            this.caseName = Objects.requireNonNull(caseName, "caseName must not be null");
            this.arm = Objects.requireNonNull(arm, "arm must not be null");
        }

        /**
         * @param value the constraint set identifier @return this builder
         */
        public Builder constraintSet(String value) {
            this.constraintSet = value;
            return this;
        }

        /**
         * @param value the declared cost profile name @return this builder
         */
        public Builder costProfile(String value) {
            this.costProfile = value;
            return this;
        }

        /**
         * @param declared     sources declared in the uncertainty model
         * @param afterPruning sources retained after impact pruning
         * @return this builder
         */
        public Builder sources(int declared, int afterPruning) {
            this.sourcesDeclared = declared;
            this.sourcesAfterPruning = afterPruning;
            return this;
        }

        /**
         * @param full       the size of the declared scenario space
         * @param considered the scenarios actually solved over
         * @param validated  the scenarios re-analyzed after the repair
         * @return this builder
         */
        public Builder scenarios(long full, long considered, long validated) {
            this.scenariosFull = full;
            this.scenariosConsidered = considered;
            this.scenariosValidated = validated;
            return this;
        }

        /**
         * @param before     violations in the unrepaired model
         * @param after      violations after the repair, across the validated space
         * @param inOwnModel violations this arm leaves in the model it was given
         * @return this builder
         */
        public Builder violations(int before, int after, int inOwnModel) {
            this.violationsBefore = before;
            this.violationsAfter = after;
            this.violationsInOwnModel = inOwnModel;
            return this;
        }

        /**
         * Records how many scenarios still contain a violation, the {@code H} of the {@code V/H} pair.
         *
         * @param scenarios the number of scenarios holding at least one residual violation
         * @return this builder
         */
        public Builder scenariosWithViolation(long scenarios) {
            this.scenariosWithViolation = scenarios;
            return this;
        }

        /**
         * Records whether the analysis behind this row unrolled a data-flow cycle.
         *
         * @param wasCyclic whether the analysed model was cyclic
         * @return this builder
         */
        public Builder cyclic(boolean wasCyclic) {
            this.cyclic = Boolean.toString(wasCyclic);
            return this;
        }

        /**
         * @param actions the number of selected repair actions
         * @param value   the reported objective value
         * @param status  the solver status
         * @return this builder
         */
        public Builder plan(int actions, double value, String status) {
            this.actionsSelected = actions;
            this.objective = value;
            this.solverStatus = status;
            return this;
        }

        /**
         * @param load     model loading duration in milliseconds
         * @param prepare  per-scenario preparation duration
         * @param solve    solver duration
         * @param apply    action application duration
         * @param validate re-analysis duration
         * @param total    end-to-end duration
         * @return this builder
         */
        public Builder timings(double load, double prepare, double solve,
                               double apply, double validate, double total) {
            this.msLoad = load;
            this.msPrepare = prepare;
            this.msSolve = solve;
            this.msApply = apply;
            this.msValidate = validate;
            this.msTotal = total;
            return this;
        }

        /**
         * @param variables         binary action variables in the joint program
         * @param coverageRows      coverage constraints
         * @param contradictionRows contradiction constraints
         * @return this builder
         */
        public Builder ilpSize(int variables, int coverageRows, int contradictionRows) {
            this.ilpVariables = variables;
            this.ilpCoverageRows = coverageRows;
            this.ilpContradictionRows = contradictionRows;
            return this;
        }

        /**
         * @param index the zero-based repetition index @return this builder
         */
        public Builder repetition(int index) {
            this.repetition = index;
            return this;
        }

        /**
         * @param reason why a series stopped at this point @return this builder
         */
        public Builder stopReason(String reason) {
            this.stopReason = reason;
            return this;
        }

        /**
         * @return the immutable record
         */
        public EvaluationRecord build() {
            return new EvaluationRecord(caseName, constraintSet, arm, costProfile,
                    sourcesDeclared, sourcesAfterPruning,
                    scenariosFull, scenariosConsidered, scenariosValidated,
                    violationsBefore, violationsAfter, violationsInOwnModel, scenariosWithViolation, cyclic,
                    actionsSelected, objective, solverStatus,
                    msLoad, msPrepare, msSolve, msApply, msValidate, msTotal,
                    ilpVariables, ilpCoverageRows, ilpContradictionRows,
                    repetition, stopReason);
        }
    }
}
