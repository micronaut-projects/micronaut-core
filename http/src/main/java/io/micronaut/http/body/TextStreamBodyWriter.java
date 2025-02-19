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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
        ByteBufferOutput output = new ByteBufferOutput(bufferFactory);
        write0(type, mediaType, object, outgoingHeaders, output);
        return output.buffer;
    }

    @Override
    public void writeTo(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        write0(type, mediaType, object, outgoingHeaders, new StreamOutput(outputStream));
    }

    private void write0(Argument<T> type, MediaType mediaType, T object, MutableHeaders outgoingHeaders, Output output) {
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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            messageBodyWriter.writeTo(bodyType, MediaType.APPLICATION_JSON_TYPE, data, outgoingHeaders, baos);
            body = baos.toByteArray();
        }

        outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType != null ? mediaType : MediaType.TEXT_EVENT_STREAM_TYPE);

        writeAttribute(output, COMMENT_PREFIX, event.getComment());
        writeAttribute(output, ID_PREFIX, event.getId());
        writeAttribute(output, EVENT_PREFIX, event.getName());
        Duration retry = event.getRetry();
        if (retry != null) {
            writeAttribute(output, RETRY_PREFIX, String.valueOf(retry.toMillis()));
        }

        // Write the data
        int start = 0;
        while (start < body.length) {
            int end = indexOf(body, (byte) '\n', start);
            if (end == -1) {
                end = body.length - 1;
            }
            output.write(DATA_PREFIX).write(body, start, end - start + 1);
            start = end + 1;
        }

        // Write new lines for event separation
        output.write(NEWLINE).write(NEWLINE);
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
    private static void writeAttribute(Output eventData, byte[] attribute, String value) {
        if (value != null) {
            eventData.write(attribute)
                .write(value, StandardCharsets.UTF_8)
                .write(NEWLINE);
        }
    }

    private sealed interface Output {
        void allocate(int expectedLength);

        Output write(byte[] b);

        Output write(byte[] b, int off, int len);

        Output write(String value, Charset charset);
    }

    private static final class ByteBufferOutput implements Output {
        final ByteBufferFactory<?, ?> bufferFactory;
        ByteBuffer<?> buffer;

        ByteBufferOutput(ByteBufferFactory<?, ?> bufferFactory) {
            this.bufferFactory = bufferFactory;
        }

        @Override
        public void allocate(int expectedLength) {
            buffer = bufferFactory.buffer(expectedLength);
        }

        @Override
        public Output write(byte[] b) {
            buffer.write(b);
            return this;
        }

        @Override
        public Output write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
            return this;
        }

        @Override
        public Output write(String value, Charset charset) {
            buffer.write(value, charset);
            return this;
        }
    }

    private record StreamOutput(OutputStream stream) implements Output {
        @Override
        public void allocate(int expectedLength) {
        }

        private void handle(IOException ioe) {
            throw new CodecException("Failed to write SSE data", ioe);
        }

        @Override
        public Output write(byte[] b) {
            try {
                stream.write(b);
            } catch (IOException e) {
                handle(e);
            }
            return this;
        }

        @Override
        public Output write(byte[] b, int off, int len) {
            try {
                stream.write(b, off, len);
            } catch (IOException e) {
                handle(e);
            }
            return this;
        }

        @Override
        public Output write(String value, Charset charset) {
            try {
                stream.write(value.getBytes(charset));
            } catch (IOException e) {
                handle(e);
            }
            return this;
        }
    }
}
