package dev.arcovia.mitigation.uncertainty.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jdt.annotation.NonNull;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Utility class for handling CSV operations on Java record types.
 */
public final class RecordCsv {

    private RecordCsv() {
    }

    /**
     * Returns the CSV column names of a record type, in declaration order.
     *
     * @param recordType the record whose components name the columns
     * @return each component's {@link JsonProperty} value, or its name in snake case
     */
    public static List<String> columns(@NonNull Class<? extends Record> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordCsv::columnNameOf)
                .toList();
    }

    /**
     * Returns the same columns as reading headings, with underscores as spaces.
     *
     * @param recordType the record whose components name the columns
     * @return one heading per component, in declaration order
     */
    public static List<String> headings(@NonNull Class<? extends Record> recordType) {
        return columns(recordType).stream().map(column -> column.replace('_', ' ')).toList();
    }

    /**
     * Renders one record as a CSV line, in declaration order.
     *
     * @param value the record to read
     * @return the cells, comma separated without a trailing newline
     */
    public static String row(@NonNull Record value) {
        return String.join(",", cells(value).stream().map(RecordCsv::quote).toList());
    }

    /**
     * Renders one record as a fixed-order report line.
     *
     * @param value the record to read
     * @return the cells, separated by {@code " | "}
     */
    public static String reportRow(@NonNull Record value) {
        return String.join(" | ", cells(value));
    }

    /**
     * Renders a complete CSV artifact for one record type.
     *
     * @param recordType the record declaring the schema
     * @param rows       the rows, in the order they should appear
     * @return the header line and one line per row, each newline terminated
     */
    public static String table(@NonNull Class<? extends Record> recordType, @NonNull List<? extends Record> rows) {
        return rows.stream()
                .map(row -> RecordCsv.row(row) + "\n")
                .collect(Collectors.joining("", String.join(",", columns(recordType)) + "\n", ""));
    }

    /**
     * Reads every component of one record as display text.
     *
     * @param value the record to read
     * @return one cell per component, in declaration order
     * @throws IllegalStateException if a component cannot be read
     */
    private static List<String> cells(Record value) {
        return Arrays.stream(value.getClass().getRecordComponents())
                .map(component -> cell(value, component))
                .toList();
    }

    private static String cell(Record value, RecordComponent component) {
        Object read;
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            read = accessor.invoke(value);
        } catch (ReflectiveOperationException | SecurityException exception) {
            throw new IllegalStateException("Could not read " + component.getName(), exception);
        }
        if (read == null) {
            return "";
        }
        if (read instanceof Double number) {
            return String.format(Locale.ROOT, "%.3f", number);
        }
        return String.valueOf(read);
    }

    private static String columnNameOf(RecordComponent component) {
        JsonProperty declared = component.getAnnotation(JsonProperty.class);
        if (declared == null) {
            declared = component.getAccessor().getAnnotation(JsonProperty.class);
        }
        return declared != null ? declared.value() : snakeCase(component.getName());
    }

    private static String snakeCase(@NonNull String name) {
        StringBuilder column = new StringBuilder();
        for (char character : name.toCharArray()) {
            if (Character.isUpperCase(character)) {
                column.append('_').append(Character.toLowerCase(character));
            } else {
                column.append(character);
            }
        }
        return column.toString();
    }

    private static String quote(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"").replace("\n", " ") + '"';
    }
}
