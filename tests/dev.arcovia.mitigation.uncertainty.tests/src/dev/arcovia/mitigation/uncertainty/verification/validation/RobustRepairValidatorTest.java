package dev.arcovia.mitigation.uncertainty.verification.validation;

import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidator;

import dev.arcovia.mitigation.ilp.Constraint;
import dev.arcovia.mitigation.uncertainty.loading.LoadedUncertaintyModel;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelLoader;
import dev.arcovia.mitigation.uncertainty.loading.UncertaintyModelSpec;
import dev.arcovia.mitigation.uncertainty.pipeline.ScenarioPreparationService;
import org.dataflowanalysis.analysis.dsl.constraint.ConstraintDSL;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RobustRepairValidatorTest {

    private final Path current = Path.of(System.getProperty("user.dir"));

    @Test
    public void reportsViolationsOfTheUnrepairedBaseAcrossAllScenarios() {
        LoadedUncertaintyModel loadedModel = loadExternalModel();
        var selectedSource = loadedModel.sources().stream()
                .filter(source -> source.getEntityName().equals("Banking_Data_Location_Uncertain"))
                .toList();

        RobustRepairValidationResult result = new RobustRepairValidator(new ScenarioPreparationService()).validate(
                loadedModel.baseModel(), selectedSource, List.of(residencyConstraint()));

        assertEquals(2, result.scenarioCount(), "One source with one alternative yields two scenarios");
        assertFalse(result.passed(), "The unrepaired base model must fail validation");
        assertTrue(result.totalViolationCount() > 0, "The default scenario carries violations");
        assertEquals(2, result.scenarioIds().size());
    }

    @Test
    public void passesWhenNoScenarioCarriesViolations() {
        LoadedUncertaintyModel loadedModel = loadExternalModel();
        var selectedSource = loadedModel.sources().stream()
                .filter(source -> source.getEntityName().equals("Banking_Data_Location_Uncertain"))
                .toList();

        // A constraint over a label that does not occur anywhere is never violated.
        Constraint neverViolated = new Constraint(new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "NonExistent")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Location", "nonEU")
                .create());

        RobustRepairValidationResult result = new RobustRepairValidator(new ScenarioPreparationService()).validate(
                loadedModel.baseModel(), selectedSource, List.of(neverViolated));

        assertTrue(result.passed(), "A constraint that no scenario violates must pass validation");
        assertEquals(0, result.totalViolationCount());
    }

    private Constraint residencyConstraint() {
        return new Constraint(new ConstraintDSL().ofData()
                .withLabel("Sensitivity", "Personal")
                .neverFlows()
                .toVertex()
                .withCharacteristic("Location", "nonEU")
                .create());
    }

    private LoadedUncertaintyModel loadExternalModel() {
        Path folder = current.resolve("models")
                .resolve("DFDExternalUncertaintyMitigation");
        return new UncertaintyModelLoader().load(new UncertaintyModelSpec(
                folder.resolve("ext.dataflowdiagram"),
                folder.resolve("ext.datadictionary"),
                folder.resolve("ext.uncertainty")));
    }
}
