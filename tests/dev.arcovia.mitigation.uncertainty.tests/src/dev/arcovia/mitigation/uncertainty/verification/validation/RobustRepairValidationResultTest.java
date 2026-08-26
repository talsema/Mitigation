package dev.arcovia.mitigation.uncertainty.verification.validation;

import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult;
import dev.arcovia.mitigation.uncertainty.validation.RobustRepairValidationResult.ScenarioValidation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RobustRepairValidationResultTest {

    @Test
    public void passesOnlyWhenAllScenariosAreViolationFree() {
        var passing = new RobustRepairValidationResult(List.of(
                new ScenarioValidation("scenario-a", 0),
                new ScenarioValidation("scenario-b", 0)));

        assertTrue(passing.passed());
        assertEquals(0, passing.totalViolationCount());
        assertEquals(2, passing.scenarioCount());
        assertEquals(List.of("scenario-a", "scenario-b"), passing.scenarioIds());
    }

    @Test
    public void failsWhenAnyScenarioStillContainsViolations() {
        var failing = new RobustRepairValidationResult(List.of(
                new ScenarioValidation("scenario-a", 0),
                new ScenarioValidation("scenario-b", 2)));

        assertFalse(failing.passed());
        assertEquals(2, failing.totalViolationCount());
        assertFalse(failing.scenarios().get(1).violationFree());
    }
}
