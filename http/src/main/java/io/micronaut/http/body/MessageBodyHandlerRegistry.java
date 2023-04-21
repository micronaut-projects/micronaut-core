package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;

import java.util.List;
import java.util.Optional;

/**
 * A registry of {@link MessageBodyReader} and {@link MessageBodyWriter}.
 *
 * @since 4.0.0
 */
@Experimental
public interface MessageBodyHandlerRegistry {
    /**
     * An empty registry.
     */
    MessageBodyHandlerRegistry EMPTY = new MessageBodyHandlerRegistry() {
        @Override
        public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaType) {
            return Optional.empty();
        }

        @Override
        public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
            return Optional.empty();
        }

    };

    /**
     * Find a reader for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body reader if it is exists.
     * @param <T> The generic type
     */
    <T> Optional<MessageBodyReader<T>> findReader(
        @NonNull Argument<T> type,
        @Nullable List<MediaType> mediaType
    );

    /**
     * Find a writer for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body writer if it is exists.
     * @param <T> The generic type
     */
    <T> Optional<MessageBodyWriter<T>> findWriter(
        @NonNull Argument<T> type,
        @NonNull List<MediaType> mediaType
    );
}
