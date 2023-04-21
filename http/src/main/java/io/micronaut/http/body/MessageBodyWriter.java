package io.micronaut.http.body;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.codec.CodecException;

import java.io.OutputStream;

/**
 * An interface that allows writing a message body for the client or the server.
 *
 * <p>Implementors can define beans that use {@link io.micronaut.http.annotation.Produces} to restrict the applicable content types.</p>
 *
 * @param <T> The generic type.
 * @see io.micronaut.http.annotation.Produces
 * @since 4.0.0
 */
@Indexed(MessageBodyWriter.class)
public interface MessageBodyWriter<T> {
    /**
     * Is the type writeable.
     *
     * @param type      The type
     * @param mediaType The media type, can  be {@code null}
     * @return True if is readable
     */
    boolean isWriteable(@NonNull Argument<T> type, @Nullable MediaType mediaType);

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type         The type being decoded.
     * @param object       The object to write
     * @param mediaType    The media type, can  be {@code null}
     * @param httpHeaders  The HTTP headers
     * @param outputStream The output stream
     * @throws CodecException If an error occurs decoding
     */
    @Nullable
    void writeTo(
        @NonNull Argument<T> type,
        T object,
        @NonNull MediaType mediaType,
        @NonNull MutableHttpHeaders httpHeaders,
        @NonNull OutputStream outputStream) throws CodecException;

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type        The type being decoded.
     * @param object      The object to write
     * @param mediaType   The media type, can  be {@code null}
     * @param httpHeaders The HTTP headers
     * @param byteBuffer  The byte buffer
     * @throws CodecException If an error occurs decoding
     */
    @Nullable
    void writeTo(
        @NonNull Argument<T> type,
        T object,
        @NonNull MediaType mediaType,
        @NonNull MutableHttpHeaders httpHeaders,
        @NonNull ByteBuffer<?> byteBuffer) throws CodecException;
}
