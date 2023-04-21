package io.micronaut.http.body;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.inject.BeanType;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.Arrays;
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
    private static final MessageBodyReader<Object> NO_READER  = new NoReader();
    private static final MessageBodyWriter<Object> NO_WRITER  = new NoWriter();
    private final Map<HandlerKey<?>, MessageBodyReader<?>> readers = new ConcurrentHashMap<>(10);
    private final Map<HandlerKey<?>, MessageBodyWriter<?>> writers = new ConcurrentHashMap<>(10);

    private final BeanLocator beanLocator;

    /**
     * Default constructor.
     * @param beanLocator The bean locator.
     */
    DefaultMessageBodyHandlerRegistry(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            @SuppressWarnings("unchecked") MessageBodyReader<T> reader = beanLocator
                .findBean(Argument.of(MessageBodyReader.class, type), new MediaTypeQualifier<>(mediaTypes, Consumes.class))
                .orElse(null);
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

    @Override
    public <T> Optional<MessageBodyWriter<T>> findWriter(Argument<T> type, List<MediaType> mediaType) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaType);
        MessageBodyWriter<?> messageBodyWriter = writers.get(key);
        if (messageBodyWriter == null) {
            @SuppressWarnings("unchecked") MessageBodyWriter<T> writer = beanLocator
                .findBean(Argument.of(MessageBodyWriter.class, type), new MediaTypeQualifier<>(mediaType, Produces.class))
                .orElse(null);
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

    private record MediaTypeQualifier<T>(List<MediaType> mediaTypes,
                                         Class<? extends Annotation> annotationType) implements Qualifier<T> {

        @Override
            public <B extends BeanType<T>> Stream<B> reduce(Class<T> beanType, Stream<B> candidates) {
                return candidates.filter(c ->
                    c.isAnnotationPresent(annotationType) && Arrays.stream(c.getAnnotationMetadata().stringValues(annotationType))
                        .anyMatch(mt -> mediaTypes.contains(new MediaType(mt))
                ));
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                MediaTypeQualifier<?> that = (MediaTypeQualifier<?>) o;
                return mediaTypes.equals(that.mediaTypes);
            }

            @Override
            public int hashCode() {
                return Objects.hash(mediaTypes);
            }
        }

    private static final class NoReader implements MessageBodyReader<Object> {
        @Override
        public boolean isReadable(Argument<Object> type, MediaType mediaType) {
            return false;
        }

        @Override
        public Object read(Argument<Object> type, MediaType mediaType, HttpHeaders httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            return null;
        }

        @Override
        public Object read(Argument<Object> type, MediaType mediaType, HttpHeaders httpHeaders, InputStream inputStream) throws CodecException {
            return null;
        }
    }

    private static final class NoWriter implements MessageBodyWriter<Object> {
        @Override
        public boolean isWriteable(Argument<Object> type, MediaType mediaType) {
            return false;
        }

        @Override
        public void writeTo(Argument<Object> type, Object object, MediaType mediaType, MutableHttpHeaders httpHeaders, OutputStream outputStream) throws CodecException {
            // no-op
        }

        @Override
        public void writeTo(Argument<Object> type, Object object, MediaType mediaType, MutableHttpHeaders httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            // no-op
        }
    }
}
