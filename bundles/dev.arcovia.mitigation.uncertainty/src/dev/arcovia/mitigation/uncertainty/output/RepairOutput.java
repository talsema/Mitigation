package dev.arcovia.mitigation.uncertainty.output;

import dev.arcovia.mitigation.uncertainty.RobustRepairResult;
import org.eclipse.jdt.annotation.NonNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Records one repair trace and optionally writes the repaired model and report.
 */
public final class RepairOutput {
    private static final RepairOutput NOOP = new RepairOutput(null);

    private final RepairOutputConfiguration configuration;
    private final List<RepairTraceStep> steps = new ArrayList<>();

    /**
     * Creates either a disabled sink or an output sink with the supplied configuration.
     *
     * @param configuration the output configuration, or {@code null} for the disabled sink
     */
    private RepairOutput(RepairOutputConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the shared output sink that records and writes nothing.
     *
     * @return the disabled no-op output sink
     */
    public static RepairOutput disabled() {
        return NOOP;
    }

    /**
     * Creates an output sink that writes artifacts to a directory.
     *
     * @param directory    the output directory
     * @param artifactName the non-blank file-name stem; must not contain path separators
     * @return the configured output sink
     * @throws IllegalArgumentException if the artifact name is invalid
     */
    public static RepairOutput writingTo(@NonNull Path directory, @NonNull String artifactName) {
        return new RepairOutput(new RepairOutputConfiguration(
                Objects.requireNonNull(directory, "directory must not be null"),
                Objects.requireNonNull(artifactName, "artifactName must not be null")));
    }

    /**
     * Adds one trace entry when output is enabled.
     *
     * @param name   the step name
     * @param detail supplies the step detail
     */
    public void record(@NonNull String name, @NonNull Supplier<@NonNull String> detail) {
        if (configuration == null) {
            return;
        }
        Objects.requireNonNull(detail, "detail must not be null");
        steps.add(new RepairTraceStep(steps.size() + 1, name,
                Objects.requireNonNull(detail.get(), "detail must not be null")));
    }

    /**
     * Writes the model, dictionary, and report when output is enabled.
     *
     */
    public void persist(@NonNull RobustRepairResult result) {
        if (configuration == null) {
            return;
        }
        Objects.requireNonNull(result, "result must not be null");
        RobustRepairOutputWriter writer = new RobustRepairOutputWriter();
        RepairOutputArtifacts plannedArtifacts = writer.artifactsFor(configuration);
        record("persist-output", () -> "directory=" + configuration.outputDirectory()
                                       + ", artifactName=" + configuration.artifactName());
        record("persist-output-completed", () -> "dataFlowDiagram=" + plannedArtifacts.dataFlowDiagramPath()
                                                 + ", dataDictionary=" + plannedArtifacts.dataDictionaryPath()
                                                 + ", report=" + plannedArtifacts.repairReportPath());
        writer.write(result, configuration, List.copyOf(steps));
    }

    /**
     * @return the path this output would write (or has written) the repaired data-flow diagram to,
     * or empty if this output is disabled. The path is deterministic from the configured directory
     * and artifact name, so it is valid both before and after {@link #persist(RobustRepairResult)}.
     */
    public Optional<Path> dataFlowDiagramPath() {
        return plannedArtifacts().map(RepairOutputArtifacts::dataFlowDiagramPath);
    }

    /**
     * @return the path of the repaired data dictionary, or empty if this output is disabled.
     */
    public Optional<Path> dataDictionaryPath() {
        return plannedArtifacts().map(RepairOutputArtifacts::dataDictionaryPath);
    }

    /**
     * @return the path of the JSON repair report, or empty if this output is disabled.
     */
    public Optional<Path> repairReportPath() {
        return plannedArtifacts().map(RepairOutputArtifacts::repairReportPath);
    }

    /**
     * Computes the configured artifact paths without writing any files.
     *
     * @return the planned paths, or empty when this output sink is disabled
     */
    private Optional<RepairOutputArtifacts> plannedArtifacts() {
        if (configuration == null) {
            return Optional.empty();
        }
        return Optional.of(new RobustRepairOutputWriter().artifactsFor(configuration));
    }

}
