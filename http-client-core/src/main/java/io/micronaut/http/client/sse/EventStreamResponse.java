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
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.sse.Event;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The response of an {@link AsyncSseClient} exchange: the status and the headers of the
 * response, and its events as the body.
 *
 * @param <B> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class EventStreamResponse<B> implements HttpResponse<BodyElements<Event<B>>> {

    private final HttpResponse<?> response;
    private final BodyElements<Event<B>> elements;

    /**
     * @param response The response, for its status, headers and attributes
     * @param elements The events
     */
    EventStreamResponse(HttpResponse<?> response, BodyElements<Event<B>> elements) {
        this.response = response;
        this.elements = elements;
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
     * @return The response
     */
    public static <B> EventStreamResponse<B> of(ByteBodyHttpResponse<?> response,
                                                MessageBodyHandlerRegistry handlerRegistry,
                                                Argument<B> eventType,
                                                long maxBufferSize) {
        CloseableByteBody body = response.byteBody().move();
        try {
            MediaType contentType = response.getContentType().orElse(null);
            HttpHeaders headers = response.getHeaders();
            BodyElements<Event<B>> elements;
            if (contentType != null && MediaType.TEXT_EVENT_STREAM_TYPE.matches(contentType)) {
                // the data of each event is JSON
                Function<byte[], B> reader = dataReader(handlerRegistry, eventType, MediaType.APPLICATION_JSON_TYPE, headers);
                elements = new ByteBodyEventElements<>(body, maxBufferSize, reader);
            } else {
                // a single body, such as JSON, is one event
                MediaType mediaType = contentType == null ? MediaType.APPLICATION_JSON_TYPE : contentType;
                elements = new SingleBodyElements<>(body, dataReader(handlerRegistry, eventType, mediaType, headers));
            }
            return new EventStreamResponse<>(response, elements);
        } catch (RuntimeException e) {
            body.close();
            throw e;
        }
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

    @Override
    public int code() {
        return response.code();
    }

    @Override
    public String reason() {
        return response.reason();
    }

    @Override
    public HttpHeaders getHeaders() {
        return response.getHeaders();
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return response.getAttributes();
    }

    @Override
    public Optional<BodyElements<Event<B>>> getBody() {
        return Optional.of(elements);
    }
}
