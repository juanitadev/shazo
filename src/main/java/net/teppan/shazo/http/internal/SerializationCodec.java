package net.teppan.shazo.http.internal;

import net.teppan.shazo.ShazoException;
import net.teppan.shazo.http.Codec;

import java.io.*;

/**
 * Default {@link Codec} implementation using Java object serialization.
 * Requires {@code T} to implement {@link Serializable}.
 *
 * @param <T> a serializable domain type
 */
public final class SerializationCodec<T extends Serializable> implements Codec<T> {

    /** Constructs a {@code SerializationCodec}. */
    public SerializationCodec() {}

    @Override
    public byte[] encode(T value) throws ShazoException {
        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
        } catch (IOException e) {
            throw new ShazoException("Serialization failed", e);
        }
        return baos.toByteArray();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T decode(byte[] bytes) throws ShazoException {
        // Safe: encode() always writes objects of type T;
        // the cast is only unsafe if a tampered byte[] is supplied.
        try (var ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new ShazoException("Deserialization failed", e);
        }
    }
}
