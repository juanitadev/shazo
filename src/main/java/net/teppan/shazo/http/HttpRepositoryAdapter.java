package net.teppan.shazo.http;

import net.teppan.shazo.NotFoundException;
import net.teppan.shazo.Repository;
import net.teppan.shazo.ShazoException;
import net.teppan.shazo.http.internal.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Repository} that calls a remote {@link HttpRepositoryServlet}
 * over HTTP, making remote storage transparent to the caller.
 *
 * <p>Each repository method encodes its argument with the configured
 * {@link Codec}, POSTs it to the endpoint, and decodes the response.
 * The {@link Codec} on the adapter must match the one used by the servlet.
 *
 * <p>{@code HttpRepositoryAdapter} implements {@link AutoCloseable}; close it
 * when no longer needed to release the underlying {@link HttpClient}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * URI endpoint = URI.create("http://host:8080/api/persons");
 * Codec<Person> codec = Codec.java(Person.class);
 *
 * try (var repo = new HttpRepositoryAdapter<>(endpoint, codec)) {
 *     repo.store(new Person("42", "Alice"));
 *     Optional<Person> found = repo.retrieve(new Person("42", null));
 * }
 * }</pre>
 *
 * @param <T> the domain type managed by the remote repository
 * @see HttpRepositoryServlet
 * @see Codec
 */
public final class HttpRepositoryAdapter<T> implements Repository<T>, AutoCloseable {

    private static final Logger log =
        LoggerFactory.getLogger(HttpRepositoryAdapter.class);

    private final URI        endpoint;
    private final Codec<T>   codec;
    private final HttpClient client;

    /**
     * Constructs an adapter using a new default {@link HttpClient}.
     *
     * @param endpoint the URL of the {@link HttpRepositoryServlet}; never {@code null}
     * @param codec    the codec for domain objects; must match the servlet's codec;
     *                 never {@code null}
     */
    public HttpRepositoryAdapter(URI endpoint, Codec<T> codec) {
        this(endpoint, codec, HttpClient.newHttpClient());
    }

    /**
     * Constructs an adapter using a caller-supplied {@link HttpClient}.
     * Use this constructor to configure timeouts, authentication, proxies, etc.
     *
     * @param endpoint the URL of the {@link HttpRepositoryServlet}; never {@code null}
     * @param codec    the codec for domain objects; must match the servlet's codec;
     *                 never {@code null}
     * @param client   the {@link HttpClient} to use for all requests; never {@code null}
     */
    public HttpRepositoryAdapter(URI endpoint, Codec<T> codec, HttpClient client) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.codec    = Objects.requireNonNull(codec,    "codec");
        this.client   = Objects.requireNonNull(client,   "client");
    }

    // ── Repository methods ────────────────────────────────────────────────────

    @Override
    public boolean contains(T query) throws ShazoException {
        log.debug("contains → {}", endpoint);
        byte[] body = buildRequest(Protocol.OP_CONTAINS, query);
        byte[] resp = post(body);
        try {
            var in = new DataInputStream(new ByteArrayInputStream(resp));
            byte status = in.readByte();
            checkException(status, in);
            return in.readByte() != 0;
        } catch (IOException e) {
            throw new ShazoException("Failed to parse contains response", e);
        }
    }

    @Override
    public void store(T entity) throws ShazoException {
        log.debug("store → {}", endpoint);
        byte[] resp = post(buildRequest(Protocol.OP_STORE, entity));
        checkVoidResponse(resp);
    }

    @Override
    public void delete(T entity) throws ShazoException {
        log.debug("delete → {}", endpoint);
        byte[] resp = post(buildRequest(Protocol.OP_DELETE, entity));
        checkVoidResponse(resp);
    }

    @Override
    public Optional<T> retrieve(T query) throws ShazoException {
        log.debug("retrieve → {}", endpoint);
        byte[] body = buildRequest(Protocol.OP_RETRIEVE, query);
        byte[] resp = post(body);
        try {
            var in = new DataInputStream(new ByteArrayInputStream(resp));
            byte status = in.readByte();
            if (status == Protocol.STATUS_NOT_FOUND) return Optional.empty();
            checkException(status, in);
            return Optional.of(codec.decode(in.readAllBytes()));
        } catch (IOException e) {
            throw new ShazoException("Failed to parse retrieve response", e);
        }
    }

    @Override
    public T retrieveRequired(T query) throws ShazoException, NotFoundException {
        return retrieve(query).orElseThrow(
            () -> new NotFoundException(query.toString()));
    }

    @Override
    public List<T> catalog(T query) throws ShazoException {
        log.debug("catalog → {}", endpoint);
        byte[] body = buildRequest(Protocol.OP_CATALOG, query);
        byte[] resp = post(body);
        try {
            var in = new DataInputStream(new ByteArrayInputStream(resp));
            byte status = in.readByte();
            checkException(status, in);
            int count   = in.readInt();
            var results = new ArrayList<T>(count);
            for (int i = 0; i < count; i++) {
                int    len   = in.readInt();
                byte[] bytes = in.readNBytes(len);
                results.add(codec.decode(bytes));
            }
            return List.copyOf(results);
        } catch (IOException e) {
            throw new ShazoException("Failed to parse catalog response", e);
        }
    }

    // ── AutoCloseable ─────────────────────────────────────────────────────────

    /**
     * Shuts down the underlying {@link HttpClient}.
     * After this call the adapter must not be used.
     */
    @Override
    public void close() {
        client.close();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] buildRequest(byte op, T entity) throws ShazoException {
        byte[] payload = codec.encode(entity);
        var baos = new ByteArrayOutputStream(1 + payload.length);
        baos.write(op);
        baos.write(payload, 0, payload.length);
        return baos.toByteArray();
    }

    private byte[] post(byte[] body) throws ShazoException {
        var request = HttpRequest.newBuilder()
            .uri(endpoint)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .header("Content-Type", "application/octet-stream")
            .build();
        try {
            var response = client.send(request,
                HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new ShazoException(
                    "HTTP " + response.statusCode() + " from " + endpoint);
            }
            return response.body();
        } catch (IOException e) {
            throw new ShazoException("HTTP request to " + endpoint + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ShazoException("HTTP request interrupted", e);
        }
    }

    private void checkVoidResponse(byte[] resp) throws ShazoException {
        try {
            var in = new DataInputStream(new ByteArrayInputStream(resp));
            checkException(in.readByte(), in);
        } catch (IOException e) {
            throw new ShazoException("Failed to parse response", e);
        }
    }

    private static void checkException(byte status, DataInputStream in)
            throws ShazoException, IOException {
        if (status == Protocol.STATUS_EXCEPTION) {
            String msg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            throw new ShazoException(msg);
        }
    }
}
