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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    private final Map<Class<?>, RawMessageBodyHandler<?>> rawHandlers;

    /**
     * Default constructor.
     *
     * @param rawHandlers Handlers for raw values
     */
    RawMessageBodyHandlerRegistry(List<RawMessageBodyHandler<?>> rawHandlers) {
        this.rawHandlers = rawHandlers.stream().collect(Collectors.toMap(RawMessageBodyHandler::getType, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> MessageBodyHandler<T> rawHandler(Argument<T> type) {
        return (MessageBodyHandler<T>) rawHandlers.get(type.getType());
    }

    protected abstract <T> MessageBodyReader<T> findReaderImpl(Argument<T> type, List<MediaType> mediaTypes);

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Optional<MessageBodyReader<T>> findReader(Argument<T> type, List<MediaType> mediaTypes) {
        HandlerKey<T> key = new HandlerKey<>(type, mediaTypes);
        MessageBodyReader<?> messageBodyReader = readers.get(key);
        if (messageBodyReader == null) {
            MessageBodyReader<T> reader = rawHandler(type);
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
            MessageBodyWriter<T> writer = rawHandler(type);
            if (writer == null && type.getType() == Object.class) {
                //writer = (MessageBodyWriter<T>) new DynamicWriter(this, mediaType);
                writer = (MessageBodyWriter<T>) NO_WRITER; // todo
            }
            if (writer == null) {
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
    static final class RawStringHandler implements RawMessageBodyHandler<String> {
        private final ApplicationConfiguration applicationConfiguration;

        RawStringHandler(ApplicationConfiguration applicationConfiguration) {
            this.applicationConfiguration = applicationConfiguration;
        }

        @Override
        public Class<String> getType() {
            return String.class;
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
        public Publisher<String> readChunked(Argument<String> type, MediaType mediaType, Headers httpHeaders, Publisher<ByteBuffer<?>> input) {
            return Flux.from(input).map(this::read0);
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
        public Class<byte[]> getType() {
            return byte[].class;
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

        @SuppressWarnings("unchecked")
        @Override
        public Class<ByteBuffer<?>> getType() {
            return (Class) ByteBuffer.class;
        }
    }
}
