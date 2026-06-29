package net.teppan.shazo;

/**
 * Constructs a single domain object from a {@link RawResult}.
 *
 * <p>{@code Infuser} is a {@link FunctionalInterface}; a lambda or method
 * reference is the idiomatic implementation, particularly for record types:
 *
 * <pre>{@code
 * Infuser<Person> infuser = result -> {
 *     var row = result.first().orElseThrow();
 *     return new Person(
 *         Producer.asString().produce(row.get("id")),
 *         Producer.asString().produce(row.get("name")),
 *         Producer.asInteger().produce(row.get("age"))
 *     );
 * };
 * }</pre>
 *
 * @param <T> the domain type constructed by this infuser
 * @see Describer#infuser()
 * @see RawResult
 * @see Producer
 */
@FunctionalInterface
public interface Infuser<T> {

    /**
     * Constructs an instance of {@code T} from the given raw result.
     *
     * @param result the raw storage result; never {@code null}
     * @return a fully populated instance of {@code T}; never {@code null}
     * @throws IllegalStateException if the result does not contain the data
     *                               required to construct {@code T}
     */
    T infuse(RawResult result);
}
