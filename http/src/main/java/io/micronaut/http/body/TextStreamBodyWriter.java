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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.sse.Event;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Handler for SSE events.
 *
 * @param <T> The type to write, may be {@link Event}
 */
@Internal
@Singleton
@Produces(MediaType.TEXT_EVENT_STREAM)
@Consumes(MediaType.TEXT_EVENT_STREAM)
final class TextStreamBodyWriter<T> implements MessageBodyWriter<T> {

    private static final byte[] DATA_PREFIX = "data: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EVENT_PREFIX = "event: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ID_PREFIX = "id: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] RETRY_PREFIX = "retry: ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] COMMENT_PREFIX = ": ".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);
    private static final List<MediaType> JSON_TYPE_LIST = List.of(MediaType.APPLICATION_JSON_TYPE);

    @Nullable
    private final MessageBodyWriter<Object> specificBodyWriter;
    private final MessageBodyHandlerRegistry registry;

    @Inject
    TextStreamBodyWriter(MessageBodyHandlerRegistry registry) {
        this(registry, null);
    }

    private TextStreamBodyWriter(MessageBodyHandlerRegistry registry, @Nullable MessageBodyWriter<Object> specificBodyWriter) {
        this.registry = registry;
        this.specificBodyWriter = specificBodyWriter;
    }

    @Override
    public MessageBodyWriter<T> createSpecific(Argument<T> type) {
        return new TextStreamBodyWriter<>(registry, registry.findWriter(getBodyType(type), JSON_TYPE_LIST).orElse(null));
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static Argument<Object> getBodyType(Argument<?> type) {
        if (type.getType().equals(Event.class)) {
            return (Argument<Object>) type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        } else {
            return (Argument<Object>) type;
        }
    }

    @Override
    public ByteBuffer<?> writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, ByteBufferFactory<?, ?> bufferFactory) throws CodecException {
        Argument<Object> bodyType = (Argument<Object>) type;
        Event<?> event;
        if (object instanceof Event<?> e) {
            event = e;
            bodyType = (Argument<Object>) type.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        } else {
            event = Event.of(object);
        }
        byte[] body;
        Object data = event.getData();
        if (data instanceof CharSequence s) {
            body = s.toString().getBytes(StandardCharsets.UTF_8);
        } else {
            MessageBodyWriter<Object> messageBodyWriter = specificBodyWriter;
            if (messageBodyWriter == null) {
                messageBodyWriter = registry.findWriter(bodyType, JSON_TYPE_LIST).orElse(null);
                if (messageBodyWriter == null) {
                    bodyType = Argument.ofInstance(data);
                    messageBodyWriter = registry.getWriter(bodyType, JSON_TYPE_LIST);
                }
            }
            ByteBuffer<?> buf = messageBodyWriter.writeTo(bodyType, MediaType.APPLICATION_JSON_TYPE, data, outgoingHeaders, bufferFactory);
            body = buf.toByteArray();
            if (buf instanceof ReferenceCounted rc) {
                rc.release();
            }
        }

        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.TEXT_EVENT_STREAM_TYPE);

        ByteBuffer<?> eventData = bufferFactory.buffer(body.length + 10);
        writeAttribute(eventData, COMMENT_PREFIX, event.getComment());
        writeAttribute(eventData, ID_PREFIX, event.getId());
        writeAttribute(eventData, EVENT_PREFIX, event.getName());
        Duration retry = event.getRetry();
        if (retry != null) {
            writeAttribute(eventData, RETRY_PREFIX, String.valueOf(retry.toMillis()));
        }

        // Write the data
        int start = 0;
        while (start < body.length) {
            int end = indexOf(body, (byte) '\n', start);
            if (end == -1) {
                end = body.length - 1;
            }
            eventData.write(DATA_PREFIX).write(body, start, end - start + 1);
            start = end + 1;
        }

        // Write new lines for event separation
        eventData.write(NEWLINE).write(NEWLINE);
        return eventData;
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        throw new UnsupportedOperationException();
    }

    private static int indexOf(byte[] haystack, @SuppressWarnings("SameParameterValue") byte needle, int start) {
        for (int i = start; i < haystack.length; i++) {
            if (haystack[i] == needle) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param eventData The byte buffer
     * @param attribute The attribute
     * @param value     The value
     */
    private static void writeAttribute(ByteBuffer<?> eventData, byte[] attribute, String value) {
        if (value != null) {
            eventData.write(attribute)
                .write(value, StandardCharsets.UTF_8)
                .write(NEWLINE);
        }
    }
}
