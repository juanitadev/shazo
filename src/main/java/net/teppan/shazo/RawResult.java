package net.teppan.shazo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable tabular result returned by a storage operation.
 *
 * <p>Each row is a column-name-to-raw-value mapping. Column names are
 * case-sensitive and match the names returned by the underlying storage
 * system (e.g., SQL column aliases in the {@code SELECT} list).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * RawResult result = ...;
 *
 * // Extract a single value from the first row:
 * String name = result.firstValue("name", Producer.asString()).orElse("unknown");
 *
 * // Map all rows to a domain type:
 * List<Person> people = result.rows().stream()
 *     .map(row -> new Person(
 *         (String) row.get("id"),
 *         (String) row.get("name"),
 *         ((Number) row.get("age")).intValue()))
 *     .toList();
 * }</pre>
 *
 * @param rows the result rows; never {@code null}, elements are never {@code null}
 */
public record RawResult(List<Map<String, Object>> rows) {

    /** Compact constructor — defensively copies the row list. */
    public RawResult {
        rows = List.copyOf(rows);
    }

    /**
     * Returns an empty result with no rows.
     *
     * @return a {@code RawResult} containing zero rows
     */
    public static RawResult empty() {
        return new RawResult(List.of());
    }

    /**
     * Constructs a result from the given rows (defensively copied).
     *
     * @param rows the result rows
     * @return a new {@code RawResult}
     */
    public static RawResult of(List<Map<String, Object>> rows) {
        return new RawResult(rows);
    }

    /**
     * Returns {@code true} if this result contains no rows.
     *
     * @return {@code true} when the row count is zero
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Returns the number of rows in this result.
     *
     * @return the row count; never negative
     */
    public int size() {
        return rows.size();
    }

    /**
     * Returns the first row, if any.
     *
     * @return an {@link Optional} containing the first row's column map, or empty
     */
    public Optional<Map<String, Object>> first() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * Extracts and converts a column value from the first row using a
     * {@link Producer}.
     *
     * @param column   the column name (case-sensitive)
     * @param producer the value converter
     * @param <T>      the target type
     * @return the produced value, or empty if there are no rows or the column
     *         is absent from the first row
     */
    public <T> Optional<T> firstValue(String column, Producer<T> producer) {
        return first()
                .filter(row -> row.containsKey(column))
                .map(row -> producer.produce(row.get(column)));
    }
}
