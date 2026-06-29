package net.teppan.shazo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * An in-memory {@link Repository} implementation for use in tests.
 * Stores entities in a {@link ConcurrentHashMap} keyed by a supplied
 * key-extraction function.
 *
 * @param <T> the domain type
 */
public class InMemoryRepository<T> implements Repository<T> {

    private final ConcurrentHashMap<Object, T> store = new ConcurrentHashMap<>();
    private final Function<T, Object> keyFn;
    private int retrieveCallCount;
    private int storeCallCount;

    public InMemoryRepository(Function<T, Object> keyFn) {
        this.keyFn = keyFn;
    }

    @Override
    public boolean contains(T query) throws ShazoException {
        return store.containsKey(keyFn.apply(query));
    }

    @Override
    public void store(T entity) throws ShazoException {
        storeCallCount++;
        store.put(keyFn.apply(entity), entity);
    }

    @Override
    public void delete(T entity) throws ShazoException {
        store.remove(keyFn.apply(entity));
    }

    @Override
    public Optional<T> retrieve(T query) throws ShazoException {
        retrieveCallCount++;
        return Optional.ofNullable(store.get(keyFn.apply(query)));
    }

    @Override
    public T retrieveRequired(T query) throws ShazoException, NotFoundException {
        return retrieve(query).orElseThrow(() -> new NotFoundException(query.toString()));
    }

    @Override
    public List<T> catalog(T ignored) throws ShazoException {
        return List.copyOf(store.values());
    }

    /** Returns the number of times {@link #retrieve} has been called. */
    public int retrieveCallCount() { return retrieveCallCount; }

    /** Returns the number of times {@link #store} has been called. */
    public int storeCallCount() { return storeCallCount; }

    /** Returns the number of entries currently in this repository. */
    public int size() { return store.size(); }
}
