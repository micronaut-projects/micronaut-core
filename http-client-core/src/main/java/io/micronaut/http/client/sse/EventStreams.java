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
import io.micronaut.core.io.buffer.ReadBufferFactory;
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
import io.micronaut.http.body.stream.PulledBodyElements;
import io.micronaut.http.client.ByteBodyElements;
import io.micronaut.http.client.ElementsResponse;
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;
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
                Function<byte[], B> reader = dataReader(handlerRegistry, eventType, MediaType.APPLICATION_JSON_TYPE, headers);
                EventStreamDecoder decoder = new EventStreamDecoder(maxBufferSize);
                elements = new ByteBodyElements<>(body, piece -> {
                    List<Event<byte[]>> events = decoder.decode(piece.toArray());
                    List<Event<B>> decoded = new ArrayList<>(events.size());
                    for (Event<byte[]> event : events) {
                        decoded.add(Event.of(event, reader.apply(event.getData())));
                    }
                    return decoded;
                }, wrap);
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
        var headers = mutableRequest.getHeaders();
        for (MediaType accepted : headers.accept()) {
            if (accepted.matches(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                return;
            }
        }
        // keep what the caller accepts, such as application/json, and accept an event stream too
        headers.add(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * The response of an exchange whose body is read as its pieces: the events of an event
     * stream, decoded as the pieces are read, or a body of another type, decoded whole as one
     * event. The data is decoded with the supplied {@link JsonMapper}; a {@code String} or a
     * {@code byte[]} is taken as it is. The events take over the pieces of the response.
     *
     * @param response      The response, with a status that is not an error
     * @param eventType     The event data type
     * @param maxBufferSize The maximum size of a line, of the data of one event, and of a body
     *                      that is not an event stream
     * @param jsonMapper    Supplies the mapper when the event type needs JSON decoding
     * @param <B>           The event data type
     * @return The response, whose body is the events
     */
    public static <B> HttpResponse<BodyElements<Event<B>>> response(HttpResponse<BodyElements<ByteBuffer<?>>> response,
                                                                   Argument<B> eventType,
                                                                   long maxBufferSize,
                                                                   Supplier<JsonMapper> jsonMapper) {
        BodyElements<ByteBuffer<?>> pieces = Objects.requireNonNull(response.body(), "The response has no elements");
        MediaType contentType = response.getContentType().orElse(null);
        boolean events = contentType != null && MediaType.TEXT_EVENT_STREAM_TYPE.matches(contentType);
        return ElementsResponse.of(response, new PieceEvents<>(pieces, events ? new EventStreamDecoder(maxBufferSize) : null, defaultReader(eventType, jsonMapper), maxBufferSize));
    }

    @SuppressWarnings("unchecked")
    private static <B> Function<byte[], B> defaultReader(Argument<B> eventType, Supplier<JsonMapper> jsonMapper) {
        Class<B> type = eventType.getType();
        if (type == String.class) {
            return data -> (B) new String(data, StandardCharsets.UTF_8);
        }
        if (type == byte[].class) {
            return data -> (B) data;
        }
        JsonMapper mapper = jsonMapper.get();
        return data -> {
            B decoded;
            try {
                decoded = mapper.readValue(data, eventType);
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
            B decoded = reader.read(eventType, mediaType, headers, ReadBufferFactory.getJdkFactory().adapt(data).toByteBuffer());
            if (decoded == null) {
                throw new HttpClientException("Event data decoded to null for type " + eventType);
            }
            return decoded;
        };
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
                        boolean more = read(error == null ? Objects.requireNonNull(value).orElse(null) : null, error);
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
                if (!read(value.orElse(null), null)) {
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
        private boolean read(@Nullable ByteBuffer<?> piece, @Nullable Throwable error) {
            if (error != null) {
                fail(wrap(error));
                return false;
            }
            try {
                if (piece == null) {
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
                byte[] bytes = piece.toByteArray();
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
}
