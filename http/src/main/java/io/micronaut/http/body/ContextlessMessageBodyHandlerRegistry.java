package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.ApplicationConfiguration;
import org.jetbrains.annotations.Nullable;

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
    public void add(MediaType mediaType, MessageBodyHandler<?> handler) {
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
