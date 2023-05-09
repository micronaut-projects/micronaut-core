package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.util.Collection;

/**
 * A body handler that is responsible for "raw" writing/reading, i.e. without a format like JSON.
 * These handlers do not care about the media type, they assume the user is passing in pre-formatted
 * data.
 *
 * @param <T> The raw message type
 */
@Internal
@Experimental
public interface RawMessageBodyHandler<T> extends MessageBodyHandler<T>, ChunkedMessageBodyReader<T> {
    /**
     * Supported types of this raw body handler. Exact match is used for reading. For writing, the
     * match is covariant. For example, if this returns {@code [String, CharSequence]}, then this
     * raw handler will be used for reading types declared as exactly {@code String} or
     * {@code CharSequence}, and will additionally be used for writing (but not reading) subtypes
     * (e.g. {@code StringBuilder}).
     *
     * @return The supported types
     */
    Collection<? extends Class<?>> getTypes();
}
