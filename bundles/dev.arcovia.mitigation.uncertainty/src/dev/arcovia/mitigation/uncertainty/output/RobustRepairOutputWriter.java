package dev.arcovia.mitigation.uncertainty.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.arcovia.mitigation.cost.ObjectiveCostBreakdown;
import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.uncertainty.RobustRepairResult;
import org.eclipse.jdt.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Writes repair artifacts to the configured directory.
 */
final class RobustRepairOutputWriter {

    /**
     * Writes the repaired model and JSON report.
     *
     * @param result        the completed repair result
     * @param configuration the output location and name
     * @param traceSteps    the trace entries to include
     * @throws IllegalStateException if an output file cannot be written
     */
    public void write(@NonNull RobustRepairResult result, @NonNull RepairOutputConfiguration configuration,
                      @NonNull List<RepairTraceStep> traceSteps) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(configuration, "configuration must not be null");
        traceSteps = List.copyOf(Objects.requireNonNull(traceSteps, "traceSteps must not be null"));

        try {
            Files.createDirectories(configuration.outputDirectory());
            result.repairedModel().save(configuration.outputDirectory().toString(), configuration.artifactName());

            RepairOutputArtifacts artifacts = artifactsFor(configuration);
            Files.writeString(artifacts.repairReportPath(), toJson(result, artifacts, traceSteps),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist robust repair outputs", exception);
        }
    }

    /**
     * Computes the three output paths.
     *
     * @param configuration the output location and name
     * @return the diagram, dictionary, and report paths
     */
    public RepairOutputArtifacts artifactsFor(RepairOutputConfiguration configuration) {
        Path basePath = configuration.outputDirectory().resolve(configuration.artifactName());
        return new RepairOutputArtifacts(
                Path.of(basePath + ".dataflowdiagram"),
                Path.of(basePath + ".datadictionary"),
                Path.of(basePath + "-repair-report.json"));
    }

    /**
     * Serializes one repair report.
     *
     * @param result     the completed repair result
     * @param artifacts  the output paths
     * @param traceSteps the recorded trace
     * @return the report JSON, or {@code {}} when serialization fails
     */
    private String toJson(RobustRepairResult result, RepairOutputArtifacts artifacts, List<RepairTraceStep> traceSteps) {
        final Object o = new Result("arcovia-robust-repair-report-v2", result, artifacts, traceSteps);
        final ObjectWriter objectWriter = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            return objectWriter.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * JSON report payload.
     */
    private record Result(
            String schema,
            String repairedDataFlowDiagram,
            String repairedDataDictionary,
            String repairReport,
            String solverStatus,
            String validationStatus,
            boolean validationPassed,
            double totalCost,
            double objectiveValue,
            ObjectiveCostBreakdown costBreakdown,
            int preRepairViolationCount,
            int postRepairViolationCount,
            int consideredScenarioCount,
            List<String> consideredScenarios,
            List<String> selectedActions,
            List<TraceStep> trace
    ) {

        /**
         * Maps domain result objects into the stable report schema.
         *
         * @param schema     the report schema identifier
         * @param result     the completed repair result
         * @param artifacts  the paths of the written artifacts
         * @param traceSteps the ordered trace from the repair run
         */
        public Result(String schema, RobustRepairResult result, RepairOutputArtifacts artifacts, List<RepairTraceStep> traceSteps) {
            this(
                    schema,
                    artifacts.dataFlowDiagramPath().toString(),
                    artifacts.dataDictionaryPath().toString(),
                    artifacts.repairReportPath().toString(),
                    result.solverStatus(),
                    result.validationStatus().name(),
                    result.validationPassed(),
                    result.objectiveValue(),
                    result.objectiveValue(),
                    result.costBreakdown(),
                    result.preRepairViolationCount(),
                    result.postRepairViolationCount(),
                    result.consideredScenarioCount(),
                    result.consideredScenarioIds(),
                    result.selectedActions().stream()
                            .map(ActionTerm::toString)
                            .toList(),
                    traceSteps.stream()
                            .map(step -> new TraceStep(step.sequence(), step.name(), step.detail()))
                            .toList()
            );
        }

        /**
         * JSON representation of one trace entry.
         *
         * @param step   the one-based trace position
         * @param name   the step name
         * @param detail the step detail
         */
        private record TraceStep(
                int step,
                String name,
                String detail
        ) {
        }

    }
}
