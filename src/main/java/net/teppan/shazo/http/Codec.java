package net.teppan.shazo.http;

import net.teppan.shazo.ShazoException;
import net.teppan.shazo.http.internal.SerializationCodec;

import java.io.Serializable;

/**
 * Converts a domain object of type {@code T} to a byte array for HTTP
 * transport and reconstructs it on the other side.
 *
 * <p>Use {@link #java()} to obtain a default codec backed by Java object
 * serialization (requires {@code T} to implement {@link Serializable}).
 * For custom encoding (JSON, Protobuf, etc.) supply your own implementation.
 *
 * <h2>Example — custom JSON codec</h2>
 * <pre>{@code
 * Codec<Person> codec = new Codec<>() {
 *     public byte[] encode(Person p) { return objectMapper.writeValueAsBytes(p); }
 *     public Person decode(byte[] b) { return objectMapper.readValue(b, Person.class); }
 * };
 * }</pre>
 *
 * @param <T> the domain type to encode and decode
 * @see HttpRepositoryServlet
 * @see HttpRepositoryAdapter
 */
public interface Codec<T> {

    /**
     * Encodes {@code value} to a byte array.
     *
     * @param value the object to encode; must not be {@code null}
     * @return the encoded representation; never {@code null}
     * @throws ShazoException if encoding fails
     */
    byte[] encode(T value) throws ShazoException;

    /**
     * Reconstructs a {@code T} from its encoded representation.
     *
     * @param bytes the byte array produced by {@link #encode}; never {@code null}
     * @return the decoded object; never {@code null}
     * @throws ShazoException if decoding fails
     */
    T decode(byte[] bytes) throws ShazoException;

    /**
     * Returns a {@code Codec} that uses Java object serialization.
     *
     * <p>Requires {@code T} to implement {@link Serializable}.
     * The returned codec is stateless and safe for concurrent use.
     *
     * @param <T> a serializable domain type
     * @return a Java-serialization-backed codec
     */
    static <T extends Serializable> Codec<T> java() {
        return new SerializationCodec<>();
    }
}
