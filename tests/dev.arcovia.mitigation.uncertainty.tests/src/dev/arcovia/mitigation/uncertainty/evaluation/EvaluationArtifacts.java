package dev.arcovia.mitigation.uncertainty.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.arcovia.mitigation.cost.ActionType;
import dev.arcovia.mitigation.cost.RepairCostSpecification;
import org.apache.log4j.Level;
import org.dataflowanalysis.analysis.core.TransposeFlowGraphFinder;
import org.dataflowanalysis.analysis.utils.LoggerManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates immutable artifacts for an opt-in evaluation run.
 */
public final class EvaluationArtifacts {
    public static final String OUTPUT_DIRECTORY_PROPERTY = "arcovia.evaluation.output";
    public static final String COST_PROFILE_PROPERTY = "arcovia.evaluation.costProfile";

    private static final ObjectMapper JSON = new ObjectMapper();

    private EvaluationArtifacts() {
    }

    /**
     * Raises the log level of the transpose-flow-graph finder for the rest of the run.
     */
    public static void suppressRepeatedAnalysisWarnings() {
        LoggerManager.getLogger(TransposeFlowGraphFinder.class).setLevel(Level.ERROR);
    }

    /**
     * Resolves the cost profile a run should use.
     *
     * @return the selected profile, defaulting to the standard preference scores
     * @throws IllegalArgumentException if the property names an unknown profile
     */
    public static RepairCostSpecification costProfile() {
        String selected = System.getProperty(COST_PROFILE_PROPERTY, "standard-preference");
        return switch (selected) {
            case "standard-preference" -> RepairCostSpecification.standardPreference();
            case "uniform" -> RepairCostSpecification.uniform();
            case "effort-demonstration" -> RepairCostSpecification.effortDemonstration();
            default -> throw new IllegalArgumentException("Unknown cost profile: " + selected
                                                          + " (expected standard-preference, uniform, or effort-demonstration)");
        };
    }

    /**
     * Creates a timestamped directory below the configured output location.
     *
     * @param current        the test process working directory
     * @param evaluationName the stable evaluation identifier
     * @return the new directory for one evaluation run
     * @throws IOException if the directory cannot be created
     */
    public static Path createRunDirectory(Path current, String evaluationName) throws IOException {
        Path outputRoot = Path.of(System.getProperty(
                OUTPUT_DIRECTORY_PROPERTY, current.resolve("target").resolve("evaluation-results").toString()));
        Path runDirectory = outputRoot.resolve(evaluationName + "-" + Instant.now().toString().replace(':', '-'));
        Path directory = Files.createDirectories(runDirectory);
        write(directory, "run-metadata.json", metadata(evaluationName));
        return directory;
    }

    /**
     * Writes one artifact.
     *
     * @param directory the evaluation run directory
     * @param fileName  the artifact name
     * @param content   the complete artifact content
     * @throws IOException if the artifact cannot be written
     */
    public static void write(Path directory, String fileName, String content) throws IOException {
        Files.writeString(directory.resolve(fileName), content);
    }

    /**
     * Writes one JSON Lines artifact.
     *
     * @param directory the evaluation run directory
     * @param fileName  the artifact name
     * @param records   the complete JSON records
     * @throws IOException if the artifact cannot be written
     */
    public static void writeJsonLines(Path directory, String fileName, List<String> records) throws IOException {
        Files.write(directory.resolve(fileName), records);
    }

    /**
     * Writes the shared measurement artifact for one evaluation run.
     * <p>
     * Every runner emits this file with the same header, so the evaluation figures are produced
     * from one schema rather than from each runner's own report format.
     *
     * @param directory the evaluation run directory
     * @param records   the measurement rows, in the order they should appear
     * @throws IOException if the artifact cannot be written
     */
    public static void writeMeasurements(Path directory, List<EvaluationRecord> records)
            throws IOException {
        StringBuilder sb = new StringBuilder(String.join(",", EvaluationRecord.COLUMNS)).append('\n');
        records.forEach(record -> sb.append(record.toCsvRow()).append('\n'));
        write(directory, "measurements.csv", sb.toString());
    }

    /**
     * Serializes one record as a single JSON Lines entry.
     * <p>
     *
     * @param record the record describing one artifact entry
     * @return the serialized JSON, on one line
     * @throws IllegalStateException if the record cannot be serialized
     */
    public static String toJson(Object record) {
        try {
            return JSON.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize evaluation JSON", exception);
        }
    }

    /**
     * Captures the runtime environment for one reproducible evaluation run.
     *
     * @param evaluationName the stable evaluation identifier
     * @return run metadata as JSON
     */
    private static String metadata(String evaluationName) {
        var profile = costProfile();
        var metadata = Map.of(
                "evaluation", evaluationName,
                "createdAt", Instant.now().toString(),
                "javaVersion", System.getProperty("java.version", "unknown"),
                "javaVendor", System.getProperty("java.vendor", "unknown"),
                "osName", System.getProperty("os.name", "unknown"),
                "osVersion", System.getProperty("os.version", "unknown"),
                "osArchitecture", System.getProperty("os.arch", "unknown"),
                "availableProcessors", Runtime.getRuntime().availableProcessors(),
                "gitRevision", System.getProperty("arcovia.evaluation.revision", "UNSPECIFIED"),
                "costProfile", Map.of(
                        "id", profile.id(),
                        "version", profile.version(),
                        "unit", profile.unit(),
                        "impactCoefficient", profile.impactCoefficient(),
                        "rippleCoefficient", profile.rippleCoefficient(),
                        "structuralCoefficient", profile.structuralCoefficient(),
                        "assuranceCoefficient", profile.assuranceCoefficient(),
                        "baseCosts", Arrays.stream(ActionType.values())
                                .collect(Collectors.toMap(
                                        Enum::toString,
                                        profile::baseCostFor,
                                        (a, b) -> b,
                                        LinkedHashMap::new)
                                ),
                        "sharedCostGroups", profile.sharedCostGroups().size())
        );
        return toJson(metadata);
    }
}
