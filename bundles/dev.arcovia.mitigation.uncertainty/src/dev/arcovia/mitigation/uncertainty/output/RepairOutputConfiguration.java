package dev.arcovia.mitigation.uncertainty.output;

import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Where and under what name the repair output is written (package-private; callers configure it
 * via {@link RepairOutput#writingTo(Path, String)}).
 *
 * @param outputDirectory directory the artifacts are written to (absolute, normalized)
 * @param artifactName    filename stem for the repaired DFD, dictionary, and JSON report
 */
record RepairOutputConfiguration(@NonNull Path outputDirectory, @NonNull String artifactName) {
    /**
     * Validates and normalizes the output location and file-name stem.
     *
     * @param outputDirectory the directory for output files
     * @param artifactName    the file-name stem; must not be blank or contain path separators
     * @throws IllegalArgumentException if the artifact name is blank or contains a path separator
     */
    public RepairOutputConfiguration {
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        artifactName = Objects.requireNonNull(artifactName, "artifactName must not be null").trim();
        if (artifactName.isEmpty()) {
            throw new IllegalArgumentException("artifactName must not be empty");
        }
        if (artifactName.contains("/") || artifactName.contains("\\")) {
            throw new IllegalArgumentException("artifactName must not contain path separators");
        }
    }
}
