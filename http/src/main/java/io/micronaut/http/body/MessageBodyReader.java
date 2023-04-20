package io.micronaut.http.body;

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

/**
 * An interface that allows reading a message body from the client or the server.
 *
 * <p>Implementors can defined beans that are annotated with {@link io.micronaut.http.annotation.Consumes} to restrict the applicable content types.</p>
 *
 * @see io.micronaut.http.annotation.Consumes
 * @param <T> The generic type.
 * @since 4.0.0
 */
@Indexed(MessageBodyReader.class)
public interface MessageBodyReader<T> {
    /**
     * Is the type readable.
     * @param type The type
     * @param mediaType The media type, can be {@code null}
     * @return True if is readable
     */
    boolean isReadable(@NonNull Argument<T> type, @Nullable MediaType mediaType);

    /**
     * Reads an object from the given byte buffer.
     *
     * @param type The type being decoded.
     * @param mediaType The media type, can be {@code null}
     * @param httpHeaders The HTTP headers
     * @param byteBuffer The byte buffer
     * @return The read object or {@code null}
     * @throws CodecException If an error occurs decoding
     */
    @Nullable T read(
        @NonNull Argument<T> type,
        @Nullable MediaType mediaType,
        @NonNull HttpHeaders httpHeaders,
        @NonNull ByteBuffer<?> byteBuffer) throws CodecException;
}
