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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.ApplicationConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link MessageBodyHandlerRegistry} implementation that does not need an application context.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
@Experimental
public final class ContextlessMessageBodyHandlerRegistry extends RawMessageBodyHandlerRegistry {
    private final List<Entry> entries = new ArrayList<>();

    /**
     * @param applicationConfiguration The configuration
     * @param byteBufferFactory        The buffer factory
     * @param otherRawHandlers         Raw handlers to add on top of the default ones
     */
    public ContextlessMessageBodyHandlerRegistry(ApplicationConfiguration applicationConfiguration, ByteBufferFactory<?, ?> byteBufferFactory, RawMessageBodyHandler<?>... otherRawHandlers) {
        super(Stream.concat(Stream.of(new RawStringHandler(applicationConfiguration), new RawByteArrayHandler(), new RawByteBufferHandler(byteBufferFactory)), Stream.of(otherRawHandlers)).toList());
    }

    /**
     * Add a {@link MessageBodyHandler} for the given media type.
     *
     * @param mediaType The media type the handler applies to
     * @param handler   The handler
     */
    public void add(@NonNull MediaType mediaType, @NonNull MessageBodyHandler<?> handler) {
        entries.add(new Entry(handler, mediaType));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> MessageBodyHandler<T> findHandler(List<MediaType> mediaTypes) {
        for (MediaType mediaType : mediaTypes) {
            for (Entry entry : entries) {
                if (mediaType.matches(entry.mediaType)) {
                    return (MessageBodyHandler<T>) entry.handler;
                }
            }
        }
        return null;
    }

    @Override
    protected <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes) {
        return findHandler(mediaTypes);
    }

    @Override
    protected <T> MessageBodyWriter<T> findWriterImpl(Argument<T> type, List<MediaType> mediaTypes) {
        return findHandler(mediaTypes);
    }

    private record Entry(MessageBodyHandler<?> handler, MediaType mediaType) {
    }
}
