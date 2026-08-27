package dev.arcovia.mitigation.uncertainty.evaluation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shared measurement schema because a column that silently disappears is only noticed when a figure cannot
 * be plotted and the whole run has to be repeated.
 */
class EvaluationRecordTest {

    @Test
    public void everyRowHasExactlyAsManyCellsAsTheHeader() {
        var row = EvaluationRecord.of("checkout", "R")
                .sources(6, 5)
                .scenarios(64, 32, 64)
                .violations(4, 0, 0)
                .plan(1, 1.0, "OPTIMAL")
                .timings(1.9, 3.8, 1.7, 0.2, 3.4, 11.6)
                .ilpSize(2, 4, 0)
                .build();

        assertEquals(EvaluationRecord.COLUMNS.size(), cells(row.toCsvRow()).size(),
                "a row must line up with the header, otherwise every downstream column is shifted");
    }

    @Test
    public void unmeasuredFieldsStayEmptyInsteadOfBeingInvented() {
        var row = EvaluationRecord.of("checkout", "B0").build();

        var cells = cells(row.toCsvRow());
        assertEquals("checkout", cells.get(0), "the case name is always known");
        assertEquals("B0", cells.get(2), "the arm is always known");
        assertTrue(cells.subList(4, cells.size()).stream().allMatch(String::isEmpty),
                "a runner that measures nothing must emit blanks, never zeros that look like data");
    }

    @Test
    public void theControlColumnSurvivesTheRoundTrip() {
        var row = EvaluationRecord.of("mitigation", "B1").violations(20, 20, 0).build();

        int index = EvaluationRecord.COLUMNS.indexOf("violations_in_own_model");
        assertEquals("0", cells(row.toCsvRow()).get(index),
                "M1.4 separates a transfer gap from a broken baseline and must never be dropped");
    }

    @Test
    public void separatorsInsideValuesCannotBreakTheColumns() {
        var row = EvaluationRecord.of("case,with,commas", "R").stopReason("said \"stop\"").build();

        assertEquals(EvaluationRecord.COLUMNS.size(), cells(row.toCsvRow()).size(),
                "quoted cells must not add columns");
    }

    @Test
    public void aDecimalCommaLocaleCannotSplitANumericCell() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            var row = EvaluationRecord.of("checkout", "R")
                    .plan(1, 1.0, "OPTIMAL")
                    .timings(1.9, 3.8, 1.7, 0.2, 3.4, 11.6)
                    .build();

            var cells = cells(row.toCsvRow());
            assertEquals(EvaluationRecord.COLUMNS.size(), cells.size(),
                    "a decimal comma must not add a column");
            assertEquals("11.600", cells.get(EvaluationRecord.COLUMNS.indexOf("ms_total")),
                    "a measured value is written with a decimal point in every locale");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    public void theWrittenFileStartsWithTheDeclaredHeader() throws Exception {
        Path directory = Files.createTempDirectory("measurements");
        var rows = List.of(EvaluationRecord.of("a", "R").build(),
                EvaluationRecord.of("b", "B1").build());

        EvaluationArtifacts.writeMeasurements(directory, rows);

        var lines = Files.readAllLines(directory.resolve("measurements.csv"));
        assertEquals(String.join(",", EvaluationRecord.COLUMNS), lines.get(0),
                "the header must match the declared column order");
        assertEquals(3, lines.size(), "one header line plus one line per record");
    }

    private static List<String> cells(String csvRow) {
        return List.of(csvRow.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1));
    }
}
