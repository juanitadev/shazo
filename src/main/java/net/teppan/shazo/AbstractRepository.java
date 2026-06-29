package net.teppan.shazo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Skeletal {@link Repository} implementation that drives all five operations
 * through a {@link Describer}, leaving only storage execution to subclasses.
 *
 * <p>Subclasses implement {@link #execute(List)} to run a list of
 * {@link Command}s against their storage backend and return a {@link RawResult}.
 * The five {@link Repository} methods are implemented here by composing
 * the describer's command generators with the execution result.
 *
 * <h2>Subclassing</h2>
 * <pre>{@code
 * public final class MyRepository<T> extends AbstractRepository<T> {
 *     public MyRepository(MyBackend backend, Describer<T> describer) {
 *         super(describer);
 *         this.backend = backend;
 *     }
 *
 *     @Override
 *     protected RawResult execute(List<Command> commands) throws ShazoException {
 *         // translate commands to backend calls and collect rows
 *     }
 * }
 * }</pre>
 *
 * @param <T> the domain type managed by this repository
 * @see Describer
 * @see net.teppan.shazo.jdbc.JdbcRepository
 */
public abstract class AbstractRepository<T> implements Repository<T> {

    private final Describer<T> describer;

    /**
     * Constructs an {@code AbstractRepository} with the given describer.
     *
     * @param describer the describer for domain type {@code T}; never {@code null}
     */
    protected AbstractRepository(Describer<T> describer) {
        this.describer = Objects.requireNonNull(describer, "describer");
    }

    /**
     * Returns the describer configured for this repository.
     *
     * @return the describer; never {@code null}
     */
    protected final Describer<T> describer() {
        return describer;
    }

    @Override
    public boolean contains(T query) throws ShazoException {
        var result = execute(describer.containsCommands(query));
        return describer.verifier().verify(result);
    }

    @Override
    public void store(T entity) throws ShazoException {
        execute(describer.storeCommands(entity));
    }

    @Override
    public void delete(T entity) throws ShazoException {
        execute(describer.deleteCommands(entity));
    }

    @Override
    public Optional<T> retrieve(T query) throws ShazoException {
        var result = execute(describer.retrieveCommands(query));
        if (!describer.verifier().verify(result)) return Optional.empty();
        return Optional.of(describer.infuser().infuse(result));
    }

    @Override
    public T retrieveRequired(T query) throws ShazoException, NotFoundException {
        return retrieve(query).orElseThrow(
            () -> new NotFoundException(query.toString()));
    }

    @Override
    public List<T> catalog(T query) throws ShazoException {
        var result = execute(describer.catalogCommands(query));
        return describer.cataloger().catalog(result);
    }

    /**
     * Executes a list of {@link Command}s against the backing storage system
     * and returns the aggregated result.
     *
     * <p>Commands are executed in list order. If any command fails, this method
     * must throw {@link ShazoException}; the state of partially applied commands
     * is backend-specific (e.g., within a JDBC transaction, they are rolled back).
     *
     * <p>{@link NoOpCommand} instances must be skipped silently.
     *
     * @param commands the commands to execute; never {@code null}
     * @return the aggregated result of all commands; never {@code null}
     * @throws ShazoException if any command fails
     */
    protected abstract RawResult execute(List<Command> commands) throws ShazoException;
}
