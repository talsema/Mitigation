package dev.arcovia.mitigation.uncertainty.output;

import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;

/**
 * The absolute paths of the files written by a repair-output persist (package-private; exposed to
 * callers as {@link java.util.Optional} paths on {@link RepairOutput}).
 *
 * @param dataFlowDiagramPath path of the written repaired data-flow diagram
 * @param dataDictionaryPath  path of the written repaired data dictionary
 * @param repairReportPath    path of the written JSON repair report
 */
record RepairOutputArtifacts(
        @NonNull Path dataFlowDiagramPath,
        @NonNull Path dataDictionaryPath,
        @NonNull Path repairReportPath
) {

    /**
     * Validates and normalizes the three paths produced by one output operation.
     *
     * @param dataFlowDiagramPath the repaired diagram path
     * @param dataDictionaryPath  the repaired dictionary path
     * @param repairReportPath    the JSON report path
     */
    public RepairOutputArtifacts {
        dataFlowDiagramPath = Objects.requireNonNull(dataFlowDiagramPath, "dataFlowDiagramPath must not be null")
                .toAbsolutePath()
                .normalize();
        dataDictionaryPath = Objects.requireNonNull(dataDictionaryPath, "dataDictionaryPath must not be null")
                .toAbsolutePath()
                .normalize();
        repairReportPath = Objects.requireNonNull(repairReportPath, "repairReportPath must not be null")
                .toAbsolutePath()
                .normalize();
    }
}
