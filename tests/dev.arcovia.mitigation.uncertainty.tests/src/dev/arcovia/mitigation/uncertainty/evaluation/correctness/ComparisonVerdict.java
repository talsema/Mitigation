package dev.arcovia.mitigation.uncertainty.evaluation.correctness;

/**
 * Names the outcome of a nominal-versus-robust comparison.
 *
 * @param kind  the comparison outcome
 * @param dueTo the repair outcome behind a failure verdict, or {@code null}
 */
record ComparisonVerdict(Kind kind, RepairOutcome dueTo) {

    protected enum Kind {
        NOMINAL_SUFFICIENT("NOMINAL-SUFFICIENT"),
        TRANSFER_GAP("TRANSFER-GAP"),
        NOMINAL_DEFECT("NOMINAL-DEFECT"),
        NOMINAL_UNAVAILABLE("NOMINAL-UNAVAILABLE"),
        ROBUST_FAILED("ROBUST-FAILED");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    static final ComparisonVerdict NOMINAL_SUFFICIENT = new ComparisonVerdict(Kind.NOMINAL_SUFFICIENT, null);
    static final ComparisonVerdict TRANSFER_GAP = new ComparisonVerdict(Kind.TRANSFER_GAP, null);
    static final ComparisonVerdict NOMINAL_DEFECT = new ComparisonVerdict(Kind.NOMINAL_DEFECT, null);

    String label() {
        return dueTo == null ? kind.label : kind.label + "(" + dueTo + ")";
    }


    static ComparisonVerdict robustFailure(RepairOutcome outcome) {
        return new ComparisonVerdict(Kind.ROBUST_FAILED, outcome);
    }

    static ComparisonVerdict nominalUnavailable(RepairOutcome outcome) {
        return new ComparisonVerdict(Kind.NOMINAL_UNAVAILABLE, outcome);
    }
}
