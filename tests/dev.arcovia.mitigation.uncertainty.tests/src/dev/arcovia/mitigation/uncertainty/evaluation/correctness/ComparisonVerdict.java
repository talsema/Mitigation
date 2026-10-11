package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

/**
 * Names the outcome of a nominal-versus-robust comparison.
 *
 * @param kind  the comparison outcome
 * @param dueTo the repair outcome behind a verdict where an arm produced no plan, or {@code null}
 */
record ComparisonVerdict(Kind kind, RepairOutcome dueTo) {

    protected enum Kind {
        NOMINAL_SUFFICIENT("NOMINAL-SUFFICIENT"),
        NOMINAL_INSUFFICIENT("NOMINAL-INSUFFICIENT"),
        NOMINAL_DEFECT("NOMINAL-DEFECT"),
        NOMINAL_UNAVAILABLE("NOMINAL-UNAVAILABLE"),
        ROBUST_UNAVAILABLE("ROBUST-UNAVAILABLE");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    static final ComparisonVerdict NOMINAL_SUFFICIENT = new ComparisonVerdict(Kind.NOMINAL_SUFFICIENT, null);

    static final ComparisonVerdict NOMINAL_INSUFFICIENT = new ComparisonVerdict(Kind.NOMINAL_INSUFFICIENT, null);

    static final ComparisonVerdict NOMINAL_DEFECT = new ComparisonVerdict(Kind.NOMINAL_DEFECT, null);

    String label() {
        return dueTo == null ? kind.label : kind.label + "(" + dueTo + ")";
    }

    static ComparisonVerdict robustUnavailable(RepairOutcome outcome) {
        return new ComparisonVerdict(Kind.ROBUST_UNAVAILABLE, outcome);
    }

    static ComparisonVerdict nominalUnavailable(RepairOutcome outcome) {
        return new ComparisonVerdict(Kind.NOMINAL_UNAVAILABLE, outcome);
    }
}
