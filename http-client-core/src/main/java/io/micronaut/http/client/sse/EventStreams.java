/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.body.stream.ByteBodyElements;
import io.micronaut.http.body.stream.PulledBodyElements;
import io.micronaut.http.client.ElementsResponse;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The server-sent events of the response of an
 * {@link io.micronaut.http.client.AsyncStreamingHttpClient} exchange, decoded from the body bytes
 * the client received.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class EventStreams {

    private EventStreams() {
    }

    /**
     * The response of an exchange whose body bytes the client received: the events of an event
     * stream, decoded as they are read, or a body of another type, decoded whole as one event.
     * The events take over the bytes of the response.
     *
     * @param response        The response, with a status that is not an error
     * @param handlerRegistry The readers of the event data
     * @param eventType       The event data type
     * @param maxBufferSize   The maximum size of a line, and of the data of one event
     * @param <B>             The event data type
     * @return The response, whose body is the events
     */
    public static <B> HttpResponse<BodyElements<Event<B>>> response(ByteBodyHttpResponse<?> response,
                                                                   MessageBodyHandlerRegistry handlerRegistry,
                                                                   Argument<B> eventType,
                                                                   long maxBufferSize) {
        return response(response, handlerRegistry, eventType, maxBufferSize, UnaryOperator.identity());
    }

    /**
     * The response of an exchange whose body bytes the client received: the events of an event
     * stream, decoded as they are read, or a body of another type, decoded whole as one event.
     * The events take over the bytes of the response.
     *
     * @param response        The response, with a status that is not an error
     * @param handlerRegistry The readers of the event data
     * @param eventType       The event data type
     * @param maxBufferSize   The maximum size of a line, and of the data of one event
     * @param decorate        Decorates a failure of the events like the other failures of the
     *                        client, e.g. with its service id
     * @param <B>             The event data type
     * @return The response, whose body is the events
     */
    @SuppressWarnings("java:S2095") // the elements own the event reader, and close it
    public static <B> HttpResponse<BodyElements<Event<B>>> response(ByteBodyHttpResponse<?> response,
                                                                   MessageBodyHandlerRegistry handlerRegistry,
                                                                   Argument<B> eventType,
                                                                   long maxBufferSize,
                                                                   UnaryOperator<HttpClientException> decorate) {
        Function<Throwable, Throwable> wrap = error -> wrap(error, decorate);
        CloseableByteBody body = response.byteBody().move();
        try {
            MediaType contentType = response.getContentType().orElse(null);
            HttpHeaders headers = response.getHeaders();
            BodyElements<Event<B>> elements;
            if (contentType != null && MediaType.TEXT_EVENT_STREAM_TYPE.matches(contentType)) {
                // the data of each event is JSON
                elements = new ByteBodyElements<>(body, reader(handlerRegistry, eventType, headers, maxBufferSize), wrap);
            } else {
                // a single body, such as JSON, is one event
                MediaType mediaType = contentType == null ? MediaType.APPLICATION_JSON_TYPE : contentType;
                elements = new SingleBodyElements<>(body, dataReader(handlerRegistry, eventType, mediaType, headers), wrap, maxBufferSize);
            }
            return ElementsResponse.of(response, elements);
        } catch (RuntimeException e) {
            body.close();
            throw e;
        }
    }

    /**
     * Accept an event stream: the {@code Accept} header of the request is kept when it accepts
     * {@code text/event-stream}, otherwise {@code text/event-stream} is added to it.
     *
     * @param request The request, changed when it is mutable
     */
    public static void acceptEvents(HttpRequest<?> request) {
        if (!(request instanceof MutableHttpRequest<?> mutableRequest)) {
            return;
        }
        for (MediaType accepted : mutableRequest.getHeaders().accept()) {
            if (accepted.matches(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                return;
            }
        }
        // keep what the caller accepts, such as application/json, and accept an event stream too
        mutableRequest.getHeaders().add(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * The response of an exchange whose body is read as its pieces: the events of an event
     * stream, decoded as the pieces are read, or a body of another type, decoded whole as one
     * event. The data is decoded with the default {@link JsonMapper}; a {@code String} or a
     * {@code byte[]} is taken as it is. The events take over the pieces of the response.
     *
     * @param response      The response, with a status that is not an error
     * @param eventType     The event data type
     * @param maxBufferSize The maximum size of a line, of the data of one event, and of a body
     *                      that is not an event stream
     * @param <B>           The event data type
     * @return The response, whose body is the events
     */
    public static <B> HttpResponse<BodyElements<Event<B>>> response(HttpResponse<BodyElements<ByteBuffer<?>>> response,
                                                                   Argument<B> eventType,
                                                                   long maxBufferSize) {
        BodyElements<ByteBuffer<?>> pieces = Objects.requireNonNull(response.body(), "The response has no elements");
        MediaType contentType = response.getContentType().orElse(null);
        boolean events = contentType != null && MediaType.TEXT_EVENT_STREAM_TYPE.matches(contentType);
        return ElementsResponse.of(response, new PieceEvents<>(pieces, events ? new EventStreamDecoder(maxBufferSize) : null, defaultReader(eventType), maxBufferSize));
    }

    @SuppressWarnings("unchecked")
    private static <B> Function<byte[], B> defaultReader(Argument<B> eventType) {
        if (eventType.getType() == String.class) {
            return data -> (B) new String(data, StandardCharsets.UTF_8);
        }
        if (eventType.getType() == byte[].class) {
            return data -> (B) data;
        }
        return data -> {
            B decoded;
            try {
                decoded = DefaultJsonMapper.INSTANCE.readValue(data, eventType);
            } catch (IOException e) {
                throw new HttpClientException("Error decoding the data of an event: " + e.getMessage(), e);
            }
            if (decoded == null) {
                throw new HttpClientException("Event data decoded to null for type " + eventType);
            }
            return decoded;
        };
    }

    /**
     * The reader of the events of an event stream: the lines are split as the pieces are read,
     * and the data of an event is decoded as JSON when the event is polled.
     *
     * @param handlerRegistry The readers of the event data
     * @param eventType       The event data type
     * @param headers         The headers of the response
     * @param maxBufferSize   The maximum size of a line, and of the data of one event
     * @param <B>             The event data type
     * @return The reader
     */
    public static <B> PieceReader<Event<B>> reader(MessageBodyHandlerRegistry handlerRegistry,
                                                   Argument<B> eventType,
                                                   Headers headers,
                                                   long maxBufferSize) {
        return new EventReader<>(new EventStreamDecoder(maxBufferSize), dataReader(handlerRegistry, eventType, MediaType.APPLICATION_JSON_TYPE, headers));
    }

    /**
     * The failure of the events, an {@link HttpClientException}.
     *
     * @param error A failure to read or decode the events
     * @return The failure of the events
     */
    static Throwable wrap(Throwable error) {
        return wrap(error, UnaryOperator.identity());
    }

    /**
     * The failure of the events, an {@link HttpClientException} decorated like the other failures
     * of the client.
     *
     * @param error    A failure to read or decode the events
     * @param decorate Decorates the failure, e.g. with the service id of the client
     * @return The failure of the events
     */
    static Throwable wrap(Throwable error, UnaryOperator<HttpClientException> decorate) {
        return decorate.apply(error instanceof HttpClientException hce ? hce : new HttpClientException("Error consuming Server Sent Events: " + error.getMessage(), error));
    }

    private static <B> Function<byte[], B> dataReader(MessageBodyHandlerRegistry handlerRegistry,
                                                      Argument<B> eventType,
                                                      MediaType mediaType,
                                                      Headers headers) {
        MessageBodyReader<B> reader = handlerRegistry.getReader(eventType, List.of(mediaType));
        return data -> {
            // a stream over the array: a buffer of it would be copied again to be decoded
            B decoded = reader.read(eventType, mediaType, headers, new ByteArrayInputStream(data));
            if (decoded == null) {
                throw new HttpClientException("Event data decoded to null for type " + eventType);
            }
            return decoded;
        };
    }

    /**
     * The default mapper, created when it is first needed.
     */
    private static final class DefaultJsonMapper {
        static final JsonMapper INSTANCE = JsonMapper.createDefault();
    }

    /**
     * The events of the pieces of a body: decoded as the pieces are read, or the whole body as
     * one event.
     *
     * @param <B> The event data type
     */
    private static final class PieceEvents<B> extends PulledBodyElements<Event<B>> {
        private final BodyElements<ByteBuffer<?>> pieces;
        private final @Nullable EventStreamDecoder decoder;
        private final Function<byte[], B> reader;
        private final long maxBufferSize;
        // the body that is not an event stream, guarded by this
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        // a piece is read, guarded by this
        private boolean reading;

        PieceEvents(BodyElements<ByteBuffer<?>> pieces, @Nullable EventStreamDecoder decoder, Function<byte[], B> reader, long maxBufferSize) {
            this.pieces = pieces;
            this.decoder = decoder;
            this.reader = reader;
            this.maxBufferSize = maxBufferSize;
        }

        @Override
        protected void demand() {
            synchronized (this) {
                if (reading) {
                    // a read that the events of the piece being read do not answer asks again
                    return;
                }
                reading = true;
            }
            while (true) {
                CompletableFuture<Optional<ByteBuffer<?>>> piece = pieces.next().toCompletableFuture();
                if (!piece.isDone()) {
                    piece.whenComplete((value, error) -> {
                        boolean more = read(value, error);
                        if (readAgain() && more) {
                            demand();
                        }
                    });
                    return;
                }
                Optional<ByteBuffer<?>> value;
                try {
                    value = piece.join();
                } catch (CompletionException | CancellationException e) {
                    read(null, e.getCause() == null ? e : e.getCause());
                    readAgain();
                    return;
                }
                if (!read(value, null)) {
                    readAgain();
                    return;
                }
                if (!stillWaiting()) {
                    return;
                }
            }
        }

        /**
         * The piece was read: whether a read still waits for an event.
         */
        private boolean readAgain() {
            synchronized (this) {
                reading = false;
            }
            return isWaiting();
        }

        /**
         * Continue reading pieces while a read waits for an event.
         */
        private synchronized boolean stillWaiting() {
            if (isWaiting()) {
                return true;
            }
            reading = false;
            return false;
        }

        /**
         * @return Whether more pieces can be read: the body did not end or fail
         */
        private boolean read(@Nullable Optional<ByteBuffer<?>> piece, @Nullable Throwable error) {
            if (error != null) {
                fail(wrap(error));
                return false;
            }
            try {
                if (piece == null || piece.isEmpty()) {
                    if (decoder == null) {
                        byte[] bytes;
                        synchronized (this) {
                            bytes = body.toByteArray();
                        }
                        if (bytes.length > 0) {
                            push(Event.of(reader.apply(bytes)));
                        }
                    }
                    // an event not terminated by a blank line is discarded
                    end();
                    return false;
                }
                byte[] bytes = piece.get().toByteArray();
                if (decoder == null) {
                    synchronized (this) {
                        long length = (long) body.size() + bytes.length;
                        if (length > maxBufferSize) {
                            throw new ContentLengthExceededException(maxBufferSize, length);
                        }
                        body.write(bytes, 0, bytes.length);
                    }
                } else {
                    List<Event<byte[]>> events = new ArrayList<>(2);
                    try {
                        decoder.decode(bytes, 0, bytes.length, events::add);
                    } finally {
                        // the events before a failure are delivered first
                        for (Event<byte[]> event : events) {
                            push(Event.of(event, reader.apply(event.getData())));
                        }
                    }
                }
            } catch (Throwable e) {
                fail(wrap(e));
                return false;
            }
            return true;
        }

        @Override
        protected void release() {
            pieces.close();
        }
    }

    /**
     * Reads the events of the pieces of an event stream. The lines are split as the pieces are
     * read, and the data of an event is decoded when the event is polled.
     *
     * @param <B> The event data type
     */
    private static final class EventReader<B> implements PieceReader<Event<B>> {
        private final EventStreamDecoder decoder;
        private final Function<byte[], B> dataReader;
        private final ArrayDeque<Event<byte[]>> events = new ArrayDeque<>(1);
        /**
         * The bytes of a piece that is not a heap buffer.
         */
        private byte[] scratch = new byte[0];

        EventReader(EventStreamDecoder decoder, Function<byte[], B> dataReader) {
            this.decoder = decoder;
            this.dataReader = dataReader;
        }

        @Override
        public void read(ReadBuffer piece) {
            try (piece) {
                // a heap buffer is decoded in place, another one is copied into an array that is
                // reused; the events before a line that exceeds the limit are delivered before the
                // failure
                int length = piece.readable();
                Boolean decoded = piece.useFastHeapBuffer(nio -> {
                    decoder.decode(nio.array(), nio.arrayOffset() + nio.position(), nio.remaining(), events::add);
                    return Boolean.TRUE;
                });
                if (decoded == null) {
                    if (scratch.length < length) {
                        scratch = new byte[Math.max(length, scratch.length * 2)];
                    }
                    piece.toArray(scratch, 0);
                    decoder.decode(scratch, 0, length, events::add);
                }
            }
        }

        @Override
        public void complete() {
            // an event not terminated by a blank line is discarded
        }

        @Override
        public @Nullable Event<B> poll() {
            Event<byte[]> event = events.poll();
            return event == null ? null : Event.of(event, dataReader.apply(event.getData()));
        }

        @Override
        public void close() {
            events.clear();
        }
    }
}
