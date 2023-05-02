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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;
import jakarta.inject.Singleton;

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

    /**
     * Default constructor.
     *
     * @param beanLocator The bean locator.
     */
    DefaultMessageBodyHandlerRegistry(BeanContext beanLocator) {
        this.beanLocator = beanLocator;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            Collection<BeanDefinition<MessageBodyReader>> beanDefinitions = beanLocator.getBeanDefinitions(Argument.of(MessageBodyReader.class, type), new MediaTypeQualifier<>(type, mediaTypes, Consumes.class));
            MessageBodyReader<T> reader;
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
            Collection<BeanDefinition<MessageBodyWriter>> beanDefinitions = beanLocator.getBeanDefinitions(Argument.of(MessageBodyWriter.class, type), new MediaTypeQualifier<>(type, mediaType, Produces.class));
            MessageBodyWriter<T> writer;
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

    record HandlerKey<T>(Argument<T> type, List<MediaType> mediaTypes) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HandlerKey<?> that = (HandlerKey<?>) o;
            return type.equals(that.type) && mediaTypes.equals(that.mediaTypes);
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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
}
