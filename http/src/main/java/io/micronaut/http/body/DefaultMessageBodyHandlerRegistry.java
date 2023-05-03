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

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Stores message body readers and writers.
 */
@SuppressWarnings("unused")
@Experimental
@Singleton
public final class DefaultMessageBodyHandlerRegistry implements MessageBodyHandlerRegistry {
    private static final MessageBodyReader<Object> NO_READER = new NoReader();
    private static final MessageBodyWriter<Object> NO_WRITER = new NoWriter();
    private final Map<HandlerKey<?>, MessageBodyReader<?>> readers = new ConcurrentHashMap<>(10);
    private final Map<HandlerKey<?>, MessageBodyWriter<?>> writers = new ConcurrentHashMap<>(10);

    private final BeanContext beanLocator;

    private final RawStringHandler rawStringHandler;
    private final RawByteArrayHandler rawByteArrayHandler;
    @Nullable
    private final RawByteBufferHandler rawByteBufferHandler;

    /**
     * Default constructor.
     *
     * @param beanLocator          The bean locator.
     * @param rawStringHandler     Handler for raw {@link String} values
     * @param rawByteArrayHandler  Handler for raw {@code byte[]} values
     * @param rawByteBufferHandler Handler for raw {@link ByteBuffer} values, {@code null} if there's no {@link ByteBufferFactory} available
     */
    DefaultMessageBodyHandlerRegistry(BeanContext beanLocator, RawStringHandler rawStringHandler, RawByteArrayHandler rawByteArrayHandler, @Nullable RawByteBufferHandler rawByteBufferHandler) {
        this.beanLocator = beanLocator;
        this.rawStringHandler = rawStringHandler;
        this.rawByteArrayHandler = rawByteArrayHandler;
        this.rawByteBufferHandler = rawByteBufferHandler;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> MessageBodyHandler<T> rawHandler(Argument<T> type) {
        if (type.getType() == String.class) {
            return (MessageBodyHandler<T>) rawStringHandler;
        } else if (type.getType() == byte[].class) {
            return (MessageBodyHandler<T>) rawByteArrayHandler;
        } else if (type.getType() == ByteBuffer.class && rawByteBufferHandler != null) {
            return (MessageBodyHandler<T>) rawByteBufferHandler;
        } else {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            MessageBodyReader<T> reader = rawHandler(type);
            if (reader == null) {
                Collection<BeanDefinition<MessageBodyReader>> beanDefinitions = beanLocator.getBeanDefinitions(Argument.of(MessageBodyReader.class, type), new MediaTypeQualifier<>(type, mediaTypes, Consumes.class));
                if (beanDefinitions.size() == 1) {
                    reader = beanLocator.getBean(beanDefinitions.iterator().next());
                } else {
                    List<BeanDefinition<MessageBodyReader>> exactMatch = beanDefinitions.stream()
                        .filter(d -> {
                            List<Argument<?>> typeArguments = d.getTypeArguments(MessageBodyReader.class);
                            if (typeArguments.isEmpty()) {
                                return false;
                            } else {
                                return type.equalsType(typeArguments.get(0));
                            }
                        }).toList();
                    if (exactMatch.size() == 1) {
                        reader = beanLocator.getBean(exactMatch.iterator().next());
                    } else {
                        // pick highest priority
                        reader = (MessageBodyReader<T>) OrderUtil.sort(beanDefinitions.stream())
                            .findFirst()
                            .map(beanLocator::getBean)
                            .orElse(null);
                    }
                }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaType);
        MessageBodyWriter<?> messageBodyWriter = writers.get(key);
        if (messageBodyWriter == null) {
            MessageBodyWriter<T> writer = rawHandler(type);
            if (writer == null) {
                Collection<BeanDefinition<MessageBodyWriter>> beanDefinitions = beanLocator.getBeanDefinitions(Argument.of(MessageBodyWriter.class, type), new MediaTypeQualifier<>(type, mediaType, Produces.class));
                if (beanDefinitions.size() == 1) {
                    writer = beanLocator.getBean(beanDefinitions.iterator().next());
                } else {
                    List<BeanDefinition<MessageBodyWriter>> exactMatch = beanDefinitions.stream()
                        .filter(d -> {
                            List<Argument<?>> typeArguments = d.getTypeArguments(MessageBodyWriter.class);
                            if (typeArguments.isEmpty()) {
                                return false;
                            } else {
                                return type.equalsType(typeArguments.get(0));
                            }
                        }).toList();
                    if (exactMatch.size() == 1) {
                        writer = beanLocator.getBean(exactMatch.iterator().next());
                    } else {
                        // pick highest priority
                        writer = (MessageBodyWriter<T>) OrderUtil.sort(beanDefinitions.stream())
                            .findFirst()
                            .map(beanLocator::getBean)
                            .orElse(null);
                    }
                }
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
            return Objects.hash(type.typeHashCode(), mediaTypes);
        }
    }

    private record MediaTypeQualifier<T>(Argument<?> type,
                                         List<MediaType> mediaTypes,
                                         Class<? extends Annotation> annotationType) implements Qualifier<T> {

        @Override
        public <B extends BeanType<T>> Stream<B> reduce(Class<T> beanType, Stream<B> candidates) {
            return candidates.filter(c -> {
                String[] applicableTypes = c.getAnnotationMetadata().stringValues(annotationType);
                if (type.getType() == Object.class) {
                    return false;
                }
                return ((applicableTypes.length == 0) || Arrays.stream(applicableTypes)
                    .anyMatch(mt -> mediaTypes.contains(new MediaType(mt)))
                );
            });
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MediaTypeQualifier<?> that = (MediaTypeQualifier<?>) o;
            return type.equalsType(that.type) && mediaTypes.equals(that.mediaTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type.typeHashCode(), mediaTypes);
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
    static final class RawStringHandler implements MessageBodyHandler<String>, PiecewiseMessageBodyReader<String> {
        private final ApplicationConfiguration applicationConfiguration;

        RawStringHandler(ApplicationConfiguration applicationConfiguration) {
            this.applicationConfiguration = applicationConfiguration;
        }

        @Override
        public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return read0(byteBuffer);
        }

        private String read0(ByteBuffer<?> byteBuffer) {
            String s = byteBuffer.toString(applicationConfiguration.getDefaultCharset());
            if (byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
            return s;
        }

        @Override
        public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                return new String(inputStream.readAllBytes(), applicationConfiguration.getDefaultCharset());
            } catch (IOException e) {
                throw new CodecException("Failed to read InputStream", e);
            }
        }

        @Override
        public void writeTo(Argument<String> type, MediaType mediaType, String object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            try {
                outputStream.write(object.getBytes(applicationConfiguration.getDefaultCharset()));
            } catch (IOException e) {
                throw new CodecException("Failed to write OutputStream", e);
            }
        }

        @Override
        public ByteBuffer<?> writeTo(Argument<String> type, MediaType mediaType, String object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
            addContentType(outgoingHeaders, mediaType);
            return bufferFactory.wrap(object.getBytes(applicationConfiguration.getDefaultCharset()));
        }

        @Override
        public Publisher<String> readPiecewise(Argument<String> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return Flux.from(input).map(this::read0);
        }
    }

    @Singleton
    static final class RawByteArrayHandler implements MessageBodyHandler<byte[]>, PiecewiseMessageBodyReader<byte[]> {
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
        public Publisher<byte[]> readPiecewise(Argument<byte[]> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return Flux.from(input).map(RawByteArrayHandler::read0);
        }
    }

    @Requires(bean = ByteBufferFactory.class)
    @Singleton
    static final class RawByteBufferHandler implements MessageBodyHandler<ByteBuffer<?>>, PiecewiseMessageBodyReader<ByteBuffer<?>> {
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
        public Publisher<ByteBuffer<?>> readPiecewise(Argument<ByteBuffer<?>> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return input;
        }
    }
}
