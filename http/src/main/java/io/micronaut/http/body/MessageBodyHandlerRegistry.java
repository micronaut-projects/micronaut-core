/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

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
     * @return A message body reader if it is existing.
     * @param <T> The generic type
     */
    @NonNull
    default <T> MessageBodyReader<T> getReader(@NonNull Argument<T> type, @Nullable List<MediaType> mediaType) {
        return findReader(type, mediaType).orElseThrow(() -> new CodecException("Cannot read value of argument [" + type + "]. No possible readers found for media type: " + mediaType));
    }

    /**
     * Find a reader for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body reader if it is existing.
     * @param <T> The generic type
     */
    <T> Optional<MessageBodyReader<T>> findReader(@NonNull Argument<T> type,
                                                  @Nullable List<MediaType> mediaType);

    /**
     * Find a reader for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body reader if it is existing.
     * @param <T> The generic type
     * @since 4.6
     */
    default <T> Optional<MessageBodyReader<T>> findReader(@NonNull Argument<T> type,
                                                          @Nullable MediaType mediaType) {
        return findReader(type, mediaType == null ? List.of() : List.of(mediaType));
    }

    /**
     * Find a reader for the type and annotation metadata at declaration point.
     * @param type The type
     * @return A message body reader if it is existing.
     * @param <T> The generic type
     * @since 4.6
     */
    default <T> Optional<MessageBodyReader<T>> findReader(@NonNull Argument<T> type) {
        return findReader(type, List.of());
    }

    /**
     * Find a writer for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body writer if it is existing.
     * @param <T> The generic type
     */
    <T> Optional<MessageBodyWriter<T>> findWriter(@NonNull Argument<T> type,
                                                  @NonNull List<MediaType> mediaType);

    /**
     * Find a writer for the type and annotation metadata at declaration point.
     * @param type The type
     * @param mediaType The media type
     * @return A message body writer if it is existing.
     * @param <T> The generic type
     * @since 4.6
     */
    default <T> Optional<MessageBodyWriter<T>> findWriter(@NonNull Argument<T> type,
                                                          @Nullable MediaType mediaType) {
        return findWriter(type, mediaType == null ? List.of() : List.of(mediaType));
    }

    /**
     * Find a writer for the type and annotation metadata at declaration point.
     * @param type The type
     * @return A message body writer if it is existing.
     * @param <T> The generic type
     * @since 4.6
     */
    default <T> Optional<MessageBodyWriter<T>> findWriter(@NonNull Argument<T> type) {
        return findWriter(type, List.of());
    }

    /**
     * Gets a writer for the type and annotation metadata at declaration point or fails with {@link CodecException}.
     * @param type The type
     * @param mediaType The media type
     * @return A message body writer if it is existing.
     * @param <T> The generic type
     */
    @NonNull
    default <T> MessageBodyWriter<T> getWriter(@NonNull Argument<T> type,
                                               @NonNull List<MediaType> mediaType) {
        return findWriter(type, mediaType)
            .orElseThrow(() -> new CodecException("Cannot encode value of argument [" + type + "]. No possible encoders found for media type: " + mediaType));
    }
}
