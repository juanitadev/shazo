package net.teppan.shazo;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Bridges a domain type {@code T} to a storage system by providing five
 * command-generation strategies — one per {@link Repository} operation —
 * together with the {@link Infuser}, {@link Cataloger}, and {@link Verifier}
 * that interpret the resulting {@link RawResult}.
 *
 * <h2>Creating a Describer via the builder</h2>
 * <pre>{@code
 * Describer<Person> d = Describer.<Person>builder()
 *     .contains(p  -> List.of(SqlCommand.of(
 *         "SELECT 1 FROM person WHERE id = ?", p.id())))
 *     .store(p     -> List.of(SqlCommand.of(
 *         "MERGE INTO person (id, name, age) VALUES (?, ?, ?)", p.id(), p.name(), p.age())))
 *     .delete(p    -> List.of(SqlCommand.of(
 *         "DELETE FROM person WHERE id = ?", p.id())))
 *     .retrieve(p  -> List.of(SqlCommand.of(
 *         "SELECT id, name, age FROM person WHERE id = ?", p.id())))
 *     .catalog(p   -> List.of(SqlCommand.of(
 *         "SELECT id, name, age FROM person ORDER BY name")))
 *     .infuser(result -> result.first().map(row -> new Person(
 *         (String) row.get("id"),
 *         (String) row.get("name"),
 *         ((Number) row.get("age")).intValue())).orElseThrow())
 *     .cataloger(result -> result.rows().stream()
 *         .map(row -> new Person(
 *             (String) row.get("id"),
 *             (String) row.get("name"),
 *             ((Number) row.get("age")).intValue())).toList())
 *     .build();
 * }</pre>
 *
 * @param <T> the domain type described
 * @see Repository
 * @see Command
 * @see #builder()
 */
public interface Describer<T> {

    /**
     * Returns commands that verify whether {@code query} exists in storage.
     * The {@link #verifier()} criterion determines whether
     * {@link Repository#contains} returns {@code true}.
     *
     * @param query the query object
     * @return commands to execute; never {@code null}
     */
    List<Command> containsCommands(T query);

    /**
     * Returns commands that persist {@code entity} in storage.
     *
     * @param entity the entity to store
     * @return commands to execute; never {@code null}
     */
    List<Command> storeCommands(T entity);

    /**
     * Returns commands that remove the entity described by {@code entity}
     * from storage.
     *
     * @param entity the entity to delete
     * @return commands to execute; never {@code null}
     */
    List<Command> deleteCommands(T entity);

    /**
     * Returns commands that retrieve an entity matching {@code query}.
     *
     * @param query the query object
     * @return commands to execute; never {@code null}
     */
    List<Command> retrieveCommands(T query);

    /**
     * Returns commands that fetch a collection of entities matching
     * {@code query}.
     *
     * @param query the query object; may act as filter criteria
     * @return commands to execute; never {@code null}
     */
    List<Command> catalogCommands(T query);

    /**
     * Returns the {@link Infuser} used to construct a single result entity
     * from a {@link RawResult}.
     *
     * @return the infuser; never {@code null}
     */
    Infuser<T> infuser();

    /**
     * Returns the {@link Cataloger} used to build a list result from a
     * {@link RawResult}.
     *
     * @return the cataloger; never {@code null}
     */
    Cataloger<T> cataloger();

    /**
     * Returns the {@link Verifier} used by {@link Repository#contains}.
     * Defaults to {@link Verifier#nonEmpty()}.
     *
     * @return the verifier; never {@code null}
     */
    default Verifier verifier() {
        return Verifier.nonEmpty();
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    /**
     * Returns a new {@link Builder} for constructing a {@code Describer<T>}.
     *
     * @param <T> the domain type
     * @return a fresh builder instance
     */
    static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder for a {@link Describer}.
     * All five command generators, the {@code infuser}, and the {@code cataloger}
     * are required; {@link #verifier} is optional (defaults to
     * {@link Verifier#nonEmpty()}).
     *
     * @param <T> the domain type
     */
    final class Builder<T> {

        private Function<T, List<Command>> containsFn;
        private Function<T, List<Command>> storeFn;
        private Function<T, List<Command>> deleteFn;
        private Function<T, List<Command>> retrieveFn;
        private Function<T, List<Command>> catalogFn;
        private Infuser<T> infuser;
        private Cataloger<T> cataloger;
        private Verifier verifier = Verifier.nonEmpty();

        private Builder() {}

        /**
         * Sets the command generator for {@link Repository#contains}.
         *
         * @param fn the generator; never {@code null}
         * @return this builder
         */
        public Builder<T> contains(Function<T, List<Command>> fn) {
            this.containsFn = Objects.requireNonNull(fn, "contains");
            return this;
        }

        /**
         * Sets the command generator for {@link Repository#store}.
         *
         * @param fn the generator; never {@code null}
         * @return this builder
         */
        public Builder<T> store(Function<T, List<Command>> fn) {
            this.storeFn = Objects.requireNonNull(fn, "store");
            return this;
        }

        /**
         * Sets the command generator for {@link Repository#delete}.
         *
         * @param fn the generator; never {@code null}
         * @return this builder
         */
        public Builder<T> delete(Function<T, List<Command>> fn) {
            this.deleteFn = Objects.requireNonNull(fn, "delete");
            return this;
        }

        /**
         * Sets the command generator for {@link Repository#retrieve}.
         *
         * @param fn the generator; never {@code null}
         * @return this builder
         */
        public Builder<T> retrieve(Function<T, List<Command>> fn) {
            this.retrieveFn = Objects.requireNonNull(fn, "retrieve");
            return this;
        }

        /**
         * Sets the command generator for {@link Repository#catalog}.
         *
         * @param fn the generator; never {@code null}
         * @return this builder
         */
        public Builder<T> catalog(Function<T, List<Command>> fn) {
            this.catalogFn = Objects.requireNonNull(fn, "catalog");
            return this;
        }

        /**
         * Sets the {@link Infuser} for single-entity retrieval.
         *
         * @param infuser the infuser; never {@code null}
         * @return this builder
         */
        public Builder<T> infuser(Infuser<T> infuser) {
            this.infuser = Objects.requireNonNull(infuser, "infuser");
            return this;
        }

        /**
         * Sets the {@link Cataloger} for multi-entity retrieval.
         *
         * @param cataloger the cataloger; never {@code null}
         * @return this builder
         */
        public Builder<T> cataloger(Cataloger<T> cataloger) {
            this.cataloger = Objects.requireNonNull(cataloger, "cataloger");
            return this;
        }

        /**
         * Overrides the default {@link Verifier}
         * (default: {@link Verifier#nonEmpty()}).
         *
         * @param verifier the verifier; never {@code null}
         * @return this builder
         */
        public Builder<T> verifier(Verifier verifier) {
            this.verifier = Objects.requireNonNull(verifier, "verifier");
            return this;
        }

        /**
         * Builds an immutable {@link Describer}.
         *
         * @return a new {@code Describer<T>}
         * @throws IllegalStateException if any required field has not been set
         */
        public Describer<T> build() {
            requireSet(containsFn, "contains");
            requireSet(storeFn,    "store");
            requireSet(deleteFn,   "delete");
            requireSet(retrieveFn, "retrieve");
            requireSet(catalogFn,  "catalog");
            requireSet(infuser,    "infuser");
            requireSet(cataloger,  "cataloger");

            // capture finals for the anonymous class
            var c = containsFn; var s = storeFn; var d = deleteFn;
            var r = retrieveFn; var g = catalogFn;
            var inf = infuser; var cat = cataloger; var ver = verifier;

            return new Describer<>() {
                @Override public List<Command> containsCommands(T q) { return c.apply(q); }
                @Override public List<Command> storeCommands(T e)    { return s.apply(e); }
                @Override public List<Command> deleteCommands(T e)   { return d.apply(e); }
                @Override public List<Command> retrieveCommands(T q) { return r.apply(q); }
                @Override public List<Command> catalogCommands(T q)  { return g.apply(q); }
                @Override public Infuser<T>    infuser()             { return inf; }
                @Override public Cataloger<T>  cataloger()           { return cat; }
                @Override public Verifier      verifier()            { return ver; }
            };
        }

        private void requireSet(Object field, String name) {
            if (field == null) {
                throw new IllegalStateException(
                    "Describer.Builder: '" + name + "' is required but was not set");
            }
        }
    }
}
