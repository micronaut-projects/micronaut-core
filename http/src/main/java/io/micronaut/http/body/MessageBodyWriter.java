package io.micronaut.http.body;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.Writable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

/**
 * An interface that allows writing a message body for the client or the server.
 *
 * <p>Implementors can define beans that use {@link io.micronaut.http.annotation.Produces} to restrict the applicable content types.</p>
 * @see io.micronaut.http.annotation.Produces
 * @since 4.0.0
 * @param <T> The generic type.
 */
@Indexed(MessageBodyWriter.class)
public interface MessageBodyWriter<T> {
    /**
     * Is the type writeable.
     * @param type The type
     * @param mediaType The media type, can  be {@code null}
     * @return True if is readable
     */
    boolean isWriteable(@NonNull Argument<T> type, @Nullable MediaType mediaType);

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type The type being decoded.
     * @param mediaType The media type, can  be {@code null}
     * @param httpHeaders The HTTP headers
     * @param writable The writable
     * @throws CodecException If an error occurs decoding
     */
    @Nullable
    void writeTo(
        @NonNull Argument<T> type,
        @NonNull MediaType mediaType,
        @NonNull HttpHeaders httpHeaders,
        @NonNull Writable writable) throws CodecException;
}
