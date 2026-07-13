package dev.arcovia.mitigation.uncertainty.output;

import dev.arcovia.mitigation.ilp.ActionTerm;
import dev.arcovia.mitigation.ilp.ActionType;
import dev.arcovia.mitigation.sat.Label;
import dev.arcovia.mitigation.sat.NodeLabel;
import dev.arcovia.mitigation.uncertainty.RobustRepairResult;
import dev.arcovia.mitigation.uncertainty.RobustRepairResult.ValidationStatus;
import org.dataflowanalysis.converter.dfd2web.DataFlowDiagramAndDictionary;
import org.dataflowanalysis.dfd.datadictionary.datadictionaryFactory;
import org.dataflowanalysis.dfd.dataflowdiagram.dataflowdiagramFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustRepairOutputWriterTest {

    @Test
    public void writesRepairedDfdDictionaryAndStructuredJsonReport(@TempDir Path outputDirectory) throws Exception {
        var model = new DataFlowDiagramAndDictionary(
                dataflowdiagramFactory.eINSTANCE.createDataFlowDiagram(),
                datadictionaryFactory.eINSTANCE.createDataDictionary());
        var action = new ActionTerm(
                "database",
                List.of(new NodeLabel(new Label("Location", "nonEU"))),
                ActionType.Removing);
        var result = new RobustRepairResult(
                model,
                List.of(action),
                1.0,
                List.of("source:default", "source:alternative"),
                1,
                0,
                "OPTIMAL",
                ValidationStatus.PASSED);

        var output = RepairOutput.writingTo(outputDirectory, "robust-repair");
        output.record("prepare-scenarios", () -> "scenarioCount=2");
        output.persist(result);

        Path report = output.repairReportPath().orElseThrow();
        assertTrue(Files.exists(output.dataFlowDiagramPath().orElseThrow()));
        assertTrue(Files.exists(output.dataDictionaryPath().orElseThrow()));
        assertTrue(Files.exists(report));

        String compact = Files.readString(report).replaceAll("\\s+", "");
        assertTrue(compact.contains("\"schema\":\"arcovia-robust-repair-report-v1\""));
        assertTrue(compact.contains("\"validationStatus\":\"PASSED\""));
        assertTrue(compact.contains("\"solverStatus\":\"OPTIMAL\""));
        assertTrue(compact.contains("\"selectedActions\""));
        assertTrue(compact.contains("\"trace\""));
        assertTrue(compact.contains("\"prepare-scenarios\""));
        assertEquals(outputDirectory.resolve("robust-repair-repair-report.json").toAbsolutePath().normalize(),
                report);
    }
}
