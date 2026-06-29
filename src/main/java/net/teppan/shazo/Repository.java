package net.teppan.shazo;

import java.util.List;
import java.util.Optional;

/**
 * The five-operation persistence contract at the heart of Shazo.
 *
 * <p>All operations accept a domain object of type {@code T} and delegate
 * the translation to storage commands to a {@link Describer}. Implementations
 * must be thread-safe.
 *
 * <h2>Operation semantics</h2>
 * <table class="striped">
 * <caption>Repository operations</caption>
 * <thead>
 *   <tr><th>Operation</th><th>Description</th><th>Returns</th></tr>
 * </thead>
 * <tbody>
 *   <tr><td>{@code contains}</td>
 *       <td>Tests whether a matching entity exists</td>
 *       <td>{@code boolean}</td></tr>
 *   <tr><td>{@code store}</td>
 *       <td>Persists an entity; creates or replaces</td>
 *       <td>{@code void}</td></tr>
 *   <tr><td>{@code delete}</td>
 *       <td>Removes a matching entity; no-op if absent</td>
 *       <td>{@code void}</td></tr>
 *   <tr><td>{@code retrieve}</td>
 *       <td>Fetches a single entity; empty if not found</td>
 *       <td>{@code Optional<T>}</td></tr>
 *   <tr><td>{@code retrieveRequired}</td>
 *       <td>Fetches a single entity; throws if not found</td>
 *       <td>{@code T}</td></tr>
 *   <tr><td>{@code catalog}</td>
 *       <td>Fetches all matching entities</td>
 *       <td>{@code List<T>}</td></tr>
 * </tbody>
 * </table>
 *
 * @param <T> the domain type managed by this repository
 * @see Describer
 * @see AbstractRepository
 */
public interface Repository<T> {

    /**
     * Returns {@code true} if a matching entity exists in storage.
     *
     * @param query an object whose fields identify the entity to look up
     * @return {@code true} if a match exists
     * @throws ShazoException if the underlying storage system reports an error
     */
    boolean contains(T query) throws ShazoException;

    /**
     * Persists {@code entity}, creating it if absent or replacing it if present.
     *
     * @param entity the entity to persist
     * @throws ShazoException if the storage operation fails
     */
    void store(T entity) throws ShazoException;

    /**
     * Removes the entity matching {@code entity} from storage.
     * Has no effect when no matching entity exists.
     *
     * @param entity an object identifying the entity to remove
     * @throws ShazoException if the storage operation fails
     */
    void delete(T entity) throws ShazoException;

    /**
     * Retrieves the entity matching {@code query}.
     * Returns {@link Optional#empty()} when no match is found.
     *
     * @param query an object whose fields identify the entity to retrieve
     * @return an {@link Optional} containing the matching entity, or empty
     * @throws ShazoException if the storage operation fails
     */
    Optional<T> retrieve(T query) throws ShazoException;

    /**
     * Retrieves the entity matching {@code query}, throwing
     * {@link NotFoundException} when none is found.
     *
     * <p>Use this variant when absence is an error condition.
     * Prefer {@link #retrieve} when absence is a normal outcome.
     *
     * @param query an object whose fields identify the entity to retrieve
     * @return the matching entity; never {@code null}
     * @throws NotFoundException if no matching entity exists
     * @throws ShazoException    if the storage operation fails
     */
    T retrieveRequired(T query) throws ShazoException, NotFoundException;

    /**
     * Returns all entities matching {@code query} as an immutable list.
     * Returns an empty list when no matches exist.
     *
     * @param query an object that serves as filter criteria
     * @return an immutable list of matching entities; never {@code null}
     * @throws ShazoException if the storage operation fails
     */
    List<T> catalog(T query) throws ShazoException;
}
