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

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
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
abstract class RawMessageBodyHandlerRegistry implements MessageBodyHandlerRegistry {
    private static final MessageBodyReader<Object> NO_READER = new NoReader();
    private static final MessageBodyWriter<Object> NO_WRITER = new NoWriter();
    private final Map<HandlerKey<?>, MessageBodyReader<?>> readers = new ConcurrentHashMap<>(10);
    private final Map<HandlerKey<?>, MessageBodyWriter<?>> writers = new ConcurrentHashMap<>(10);

    private final List<RawEntry> rawHandlers;

    /**
     * Default constructor.
     *
     * @param rawHandlers Handlers for raw values
     */
    RawMessageBodyHandlerRegistry(List<RawMessageBodyHandler<?>> rawHandlers) {
        this.rawHandlers = rawHandlers.stream().flatMap(h -> h.getTypes().stream().map(t -> new RawEntry(t, h))).toList();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> MessageBodyHandler<T> rawHandler(Argument<T> type, boolean covariant) {
        for (RawEntry entry : rawHandlers) {
            if (covariant ? entry.type.isAssignableFrom(type.getType()) : entry.type == type.getType()) {
                return (MessageBodyHandler<T>) entry.handler;
            }
        }
        return null;
    }

    protected abstract <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes);

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            MessageBodyReader<T> reader = rawHandler(type, false);
            if (reader == null) {
                reader = findReaderImpl(type, mediaTypes);
            }
            if (reader != null) {
                readers.put(key, reader);
                return Optional.of(reader);
            } else {
                readers.put(key, NO_READER);
                return Optional.empty();
            }
        } else if (messageBodyReader instanceof NoReader) {
            return Optional.empty();
        } else {
            //noinspection unchecked
            return Optional.of((MessageBodyReader<T>) messageBodyReader);
        }
    }

    protected abstract <T> MessageBodyWriter<T> findWriterImpl(Argument<T> type, List<MediaType> mediaTypes);

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaType);
        MessageBodyWriter<?> messageBodyWriter = writers.get(key);
        if (messageBodyWriter == null) {
            MessageBodyWriter<T> writer = rawHandler(type, true);
            if (writer == null && type.getType() != Object.class) {
                writer = findWriterImpl(type, mediaType);
            }
            if (writer != null) {
                writers.put(key, writer);
                return Optional.of(writer);
            } else {
                writers.put(key, NO_WRITER);
                return Optional.empty();
            }
        } else if (messageBodyWriter instanceof NoWriter) {
            return Optional.empty();
        } else {
            //noinspection unchecked
            return Optional.of((MessageBodyWriter<T>) messageBodyWriter);
        }
    }

    private static void addContentType(MutableHeaders outgoingHeaders, @Nullable MediaType mediaType) {
        if (mediaType != null && !outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
        }
    }

    private record RawEntry(Class<?> type, MessageBodyHandler<?> handler) {
    }

    record HandlerKey<T>(Argument<T> type, List<MediaType> mediaTypes) {
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

    @Singleton
    @BootstrapContextCompatible
    @Bean(typed = RawMessageBodyHandler.class)
    static final class RawStringHandler implements RawMessageBodyHandler<Object> {
        private final ApplicationConfiguration applicationConfiguration;

        RawStringHandler(ApplicationConfiguration applicationConfiguration) {
            this.applicationConfiguration = applicationConfiguration;
        }

        @Override
        public Collection<Class<?>> getTypes() {
            return List.of(String.class, CharSequence.class);
        }

        @Override
        public String read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return read0(byteBuffer, getCharset(mediaType));
        }

        private String read0(ByteBuffer<?> byteBuffer, Charset charset) {
            String s = byteBuffer.toString(charset);
            if (byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
            return s;
        }

        @Override
        public String read(Argument<Object> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                return new String(inputStream.readAllBytes(), getCharset(mediaType));
            } catch (IOException e) {
                throw new CodecException("Failed to read InputStream", e);
            }
        }

        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            try {
                outputStream.write(object.toString().getBytes(getCharset(mediaType)));
            } catch (IOException e) {
                throw new CodecException("Failed to write OutputStream", e);
            }
        }

        @Override
        public ByteBuffer<?> writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            return bufferFactory.wrap(object.toString().getBytes(getCharset(mediaType)));
        }

        @Override
        public Publisher<Object> readChunked(Argument<Object> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return Flux.from(input).map(byteBuffer -> read0(byteBuffer, getCharset(mediaType)));
        }

        private Charset getCharset(MediaType mediaType) {
            return mediaType.getCharset().orElseGet(applicationConfiguration::getDefaultCharset);
        }
    }

    @Singleton
    @BootstrapContextCompatible
    @Bean(typed = RawMessageBodyHandler.class)
    static final class RawByteArrayHandler implements RawMessageBodyHandler<byte[]> {
        @Override
        public byte[] read(Argument<byte[]> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return read0(byteBuffer);
        }

        private static byte[] read0(ByteBuffer<?> byteBuffer) {
            byte[] arr = byteBuffer.toByteArray();
            if (byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
            return arr;
        }

        @Override
        public byte[] read(Argument<byte[]> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                return inputStream.readAllBytes();
            } catch (IOException e) {
                throw new CodecException("Failed to read InputStream", e);
            }
        }

        @Override
        public void writeTo(Argument<byte[]> type, MediaType mediaType, byte[] object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            try {
                outputStream.write(object);
            } catch (IOException e) {
                throw new CodecException("Failed to write OutputStream", e);
            }
        }

        @Override
        public ByteBuffer<?> writeTo(Argument<byte[]> type, MediaType mediaType, byte[] object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            return bufferFactory.wrap(object);
        }

        @Override
        public Publisher<byte[]> readChunked(Argument<byte[]> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return Flux.from(input).map(RawByteArrayHandler::read0);
        }

        @Override
        public Collection<Class<byte[]>> getTypes() {
            return List.of(byte[].class);
        }
    }

    @Requires(bean = ByteBufferFactory.class)
    @Singleton
    @BootstrapContextCompatible
    @Bean(typed = RawMessageBodyHandler.class)
    static final class RawByteBufferHandler implements RawMessageBodyHandler<ByteBuffer<?>> {
        private final ByteBufferFactory<?, ?> byteBufferFactory;

        RawByteBufferHandler(ByteBufferFactory<?, ?> byteBufferFactory) {
            this.byteBufferFactory = byteBufferFactory;
        }

        @Override
        public ByteBuffer<?> read(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return read0(byteBuffer);
        }

        private static ByteBuffer<?> read0(ByteBuffer<?> byteBuffer) {
            return byteBuffer;
        }

        @Override
        public ByteBuffer<?> read(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                return byteBufferFactory.wrap(inputStream.readAllBytes());
            } catch (IOException e) {
                throw new CodecException("Failed to read InputStream", e);
            }
        }

        @Override
        public void writeTo(Argument<ByteBuffer<?>> type, MediaType mediaType, ByteBuffer<?> object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            try {
                object.toInputStream().transferTo(outputStream);
                if (object instanceof ReferenceCounted rc) {
                    rc.release();
                }
            } catch (IOException e) {
                throw new CodecException("Failed to write OutputStream", e);
            }
        }

        @Override
        public ByteBuffer<?> writeTo(Argument<ByteBuffer<?>> type, MediaType mediaType, ByteBuffer<?> object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            return object;
        }

        @Override
        public Publisher<ByteBuffer<?>> readChunked(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return input;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Collection<Class<ByteBuffer<?>>> getTypes() {
            return List.of((Class) ByteBuffer.class);
        }
    }
}
