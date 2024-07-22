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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for {@link MessageBodyHandlerRegistry} that handles caching and exposes the raw
 * handlers (String, byte[] and such).
 *
 * @author Graeme Rocher
 * @since 4.0.0
 */
@Internal
@Experimental
abstract sealed class AbstractMessageBodyHandlerRegistry implements MessageBodyHandlerRegistry permits ContextlessMessageBodyHandlerRegistry, DefaultMessageBodyHandlerRegistry {
    private static final MessageBodyReader<Object> NO_READER = new NoReader();
    private static final MessageBodyWriter<Object> NO_WRITER = new NoWriter();
    private final Map<HandlerKey<?>, MessageBodyReader<?>> readers = new ConcurrentHashMap<>(10);
    private final Map<HandlerKey<?>, MessageBodyWriter<?>> writers = new ConcurrentHashMap<>(10);

    protected abstract <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes);

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            MessageBodyReader<T> reader = findReaderImpl(type, mediaTypes);
            if (reader != null) {
                readers.put(key, reader);
                return Optional.of(reader);
            } else {
                readers.put(key, NO_READER);
                return Optional.empty();
            }
        } else if (messageBodyReader == NO_READER) {
            return Optional.empty();
        } else {
            //noinspection unchecked
            return Optional.of((MessageBodyReader<T>) messageBodyReader);
        }
    }

    protected abstract <T> MessageBodyWriter<T> findWriterImpl(Argument<T> type, List<MediaType> mediaTypes);

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaTypes) {
        if (type.getType() == Object.class) {
            return Optional.empty();
        }
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyWriter<?> messageBodyWriter = writers.get(key);
        if (messageBodyWriter == null) {
            MessageBodyWriter<T> writer = findWriterImpl(type, mediaTypes);
            if (writer != null) {
                writers.put(key, writer);
                return Optional.of(writer);
            } else {
                writers.put(key, NO_WRITER);
                return Optional.empty();
            }
        } else if (messageBodyWriter == NO_WRITER) {
            return Optional.empty();
        } else {
            //noinspection unchecked
            return Optional.of((MessageBodyWriter<T>) messageBodyWriter);
        }
    }

    private record HandlerKey<T>(Argument<T> type, List<MediaType> mediaTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            HandlerKey<?> that = (HandlerKey<?>) o;
            return type.equalsType(that.type) && mediaTypes.equals(that.mediaTypes);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(type.typeHashCode(), mediaTypes);
        }
    }

    private static final class NoReader implements MessageBodyReader<Object> {
        @Override
        public boolean isReadable(Argument<Object> type, MediaType mediaType) {
            return false;
        }

        @Override
        public Object read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return null;
        }

        @Override
        public Object read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            return null;
        }
    }

    private static final class NoWriter implements MessageBodyWriter<Object> {
        @Override
        public boolean isWriteable(Argument<Object> type, MediaType mediaType) {
            return false;
        }

        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            throw new UnsupportedOperationException();
        }
    }

}
