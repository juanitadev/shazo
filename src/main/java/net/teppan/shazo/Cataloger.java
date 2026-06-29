package net.teppan.shazo;

import java.util.List;

/**
 * Converts a multi-row {@link RawResult} into a typed list of domain objects.
 *
 * <p>{@code Cataloger} is a {@link FunctionalInterface}; a lambda or method
 * reference is the idiomatic implementation:
 *
 * <pre>{@code
 * Cataloger<Person> cataloger = result -> result.rows().stream()
 *     .map(row -> new Person(
 *         Producer.asString().produce(row.get("id")),
 *         Producer.asString().produce(row.get("name")),
 *         Producer.asInteger().produce(row.get("age"))
 *     ))
 *     .toList();
 * }</pre>
 *
 * @param <T> the element type of the list produced by this cataloger
 * @see Describer#cataloger()
 * @see RawResult
 */
@FunctionalInterface
public interface Cataloger<T> {

    /**
     * Converts {@code result} to an immutable typed list.
     *
     * @param result the raw storage result; never {@code null}
     * @return an immutable list of domain objects; never {@code null},
     *         returns an empty list when {@code result} is empty
     */
    List<T> catalog(RawResult result);
}
