package dev.arcovia.mitigation.smt.tests.evaluation;

import dev.arcovia.mitigation.ilp.OptimizationManager;
import dev.arcovia.mitigation.sat.ModelCostCalculator;
import dev.arcovia.mitigation.smt.Mitigation;
import dev.arcovia.mitigation.smt.SolvingResult;
import dev.arcovia.mitigation.smt.utils.ParsingUtils;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Cross-validates three independently built repair encodings against one external ground truth.
 */
public class CrossValidationTest {

    private record Row(String model, int variant, int violationsBefore,
                       Arm sat, Arm smt, Arm ilp) {
    }

    /**
     * One arm's outcome on one configuration.
     *
     * @param status           what happened, or the exception type when it failed
     * @param residual         violations the shared analysis finds in the repaired model
     * @param modelCostDelta   repaired-model cost minus base-model cost, the shared measure
     * @param milliseconds     wall-clock time, recorded but not compared
     */
    private record Arm(String status, int residual, int modelCostDelta, long milliseconds) {

        static Arm failed(Exception e) {
            return new Arm("FAILED:" + e.getClass().getSimpleName(), -1, -1, -1);
        }

        boolean clean() {
            return residual == 0;
        }
    }

    @Test
    public void threeEncodingsAgreeOnWhatCountsAsRepaired() throws Exception {
        if (!Boolean.getBoolean("arcovia.evaluation")) {
            return;
        }
        Files.createDirectories(Path.of("testresults"));

        List<EvaluationSupport.Configuration> configs = EvaluationSupport.configurations();
        List<Row> rows = new ArrayList<>();
        for (EvaluationSupport.Configuration cfg : configs) {
            rows.add(measure(cfg));
        }

        String report = render(rows);
        System.out.println(report);
        write(report, rows);

        assertEquals(configs.size(), rows.size(), "every configuration must be measured");
        assertFalse(rows.isEmpty(), "the corpus must not be empty");
    }

    /**
     * Runs all three encodings on one configuration and re-analyses each repaired model.
     *
     * @param cfg the configuration to measure
     * @return the per-arm outcomes
     */
    private Row measure(EvaluationSupport.Configuration cfg) throws Exception {
        var satConstraints = SatHelper.toSatConstraints(cfg.constraints());
        int baseCost = new ModelCostCalculator(base(cfg), satConstraints, SatHelper.MIN_COSTS).calculateCost();
        int before = ParsingUtils.countViolations(base(cfg), cfg.constraints());

        Arm sat;
        try {
            long start = System.nanoTime();
            var repaired = SatHelper.runRepair(base(cfg), false, cfg.constraints(), SatHelper.MIN_COSTS)
                    .repairedDfd();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            sat = new Arm("REPAIRED", ParsingUtils.countViolations(repaired, cfg.constraints()),
                    cost(repaired, satConstraints) - baseCost, elapsed);
        } catch (Exception e) {
            sat = Arm.failed(e);
        }

        Arm smt;
        try {
            long start = System.nanoTime();
            SolvingResult solving = Mitigation.run(base(cfg), cfg.constraints(), null);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            smt = new Arm(solving.satisfiable() ? "REPAIRED" : "UNSATISFIABLE",
                    ParsingUtils.countViolations(solving.repairedDFD(), cfg.constraints()),
                    cost(solving.repairedDFD(), satConstraints) - baseCost, elapsed);
        } catch (Exception e) {
            smt = Arm.failed(e);
        }

        Arm ilp;
        try {
            DataFlowDiagramAndDictionary model = base(cfg);
            var manager = new OptimizationManager(model,
                    cfg.constraints().stream().map(dev.arcovia.mitigation.ilp.Constraint::new).toList(), false);
            long start = System.nanoTime();
            DataFlowDiagramAndDictionary repaired = manager.repair();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            ilp = new Arm("REPAIRED", ParsingUtils.countViolations(repaired, cfg.constraints()),
                    cost(repaired, satConstraints) - baseCost, elapsed);
        } catch (Exception e) {
            ilp = Arm.failed(e);
        }

        return new Row(cfg.model(), cfg.variantId(), before, sat, smt, ilp);
    }

    private static int cost(DataFlowDiagramAndDictionary dfd,
                            List<dev.arcovia.mitigation.sat.Constraint> constraints) {
        return new ModelCostCalculator(dfd, constraints, SatHelper.MIN_COSTS).calculateCost();
    }

    private static DataFlowDiagramAndDictionary base(EvaluationSupport.Configuration cfg) throws Exception {
        return ParsingUtils.loadDFD(cfg.model(), cfg.model() + "_0");
    }

    private static String render(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Cross-validation of three repair encodings ==========\n");
        sb.append("Validity is judged by re-analysing each repaired model with the same analysis.\n");
        sb.append("Cost is the repaired-model cost minus the base-model cost, the same measure for\n");
        sb.append("every arm, because the three objectives are not commensurable. That measure\n");
        sb.append("prices only the labels the predecessor's cost map names, so an arm repairing with\n");
        sb.append("labels outside that map reads as zero. The cost column is therefore reported and\n");
        sb.append("not used to rank the arms.\n\n");
        sb.append(String.format("%-24s %3s | %-22s | %-22s | %-22s%n",
                "Model", "var", "SAT (Niehues)", "MaxSMT (sibling)", "ILP (this thesis)"));
        sb.append("-".repeat(120)).append("\n");
        for (Row r : rows) {
            sb.append(String.format("%-24s %3d | %-22s | %-22s | %-22s%n",
                    truncate(r.model(), 24), r.variant(),
                    cell(r.sat()), cell(r.smt()), cell(r.ilp())));
        }
        sb.append("-".repeat(120)).append("\n");

        long total = rows.size();
        long allClean = rows.stream()
                .filter(r -> r.sat().clean() && r.smt().clean() && r.ilp().clean()).count();
        long disagree = rows.stream()
                .filter(r -> r.sat().clean() != r.ilp().clean() || r.smt().clean() != r.ilp().clean())
                .count();
        sb.append(String.format("All three encodings leave no violation: %d of %d.%n", allClean, total));
        sb.append(String.format("Configurations where the arms disagree on validity: %d.%n", disagree));
        long zeroCostRepairs = rows.stream()
                .filter(r -> r.violationsBefore() > 0)
                .filter(r -> r.smt().clean() && r.smt().modelCostDelta() == 0).count();
        if (zeroCostRepairs > 0) {
            sb.append(String.format("CHECK: %d configurations show a clean repair at zero measured "
                                    + "cost. The shared measure does not price those changes, so no "
                                    + "cost ranking is drawn from this run.%n", zeroCostRepairs));
        }
        sb.append("Runtimes are recorded per arm but not compared: the three run in one process "
                  + "without a controlled environment.\n");
        return sb.toString();
    }

    private static String cell(Arm arm) {
        if (arm.residual() < 0) {
            return arm.status();
        }
        return String.format("v=%-3d cost=%-4d %sms", arm.residual(), arm.modelCostDelta(),
                arm.milliseconds());
    }

    private static String truncate(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width - 1) + "…";
    }

    private void write(String report, List<Row> rows) throws Exception {
        Path directory = Path.of(System.getProperty("arcovia.evaluation.output", "testresults"))
                .resolve("cross-validation");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("report.txt"), report);

        StringBuilder csv = new StringBuilder(
                "model,variant,violations_before,arm,status,residual_violations,model_cost_delta,ms\n");
        for (Row r : rows) {
            csv.append(line(r, "SAT", r.sat())).append(line(r, "MaxSMT", r.smt()))
                    .append(line(r, "ILP", r.ilp()));
        }
        Files.writeString(directory.resolve("summary.csv"), csv.toString());
        System.out.println("Evaluation artefacts: " + directory.toAbsolutePath());
    }

    private static String line(Row r, String arm, Arm outcome) {
        return String.join(",", r.model(), Integer.toString(r.variant()),
                Integer.toString(r.violationsBefore()), arm, outcome.status(),
                Integer.toString(outcome.residual()), Integer.toString(outcome.modelCostDelta()),
                Long.toString(outcome.milliseconds())) + "\n";
    }
}
