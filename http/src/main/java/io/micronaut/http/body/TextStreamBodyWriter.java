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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
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
    private static final byte[] EMPTY_DATA = "data:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EMPTY_COMMENT = ":".getBytes(StandardCharsets.UTF_8);
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
        Argument<Object> bodyType = getBodyType(type);
        return new TextStreamBodyWriter<>(registry, registry.findWriter(bodyType, JSON_TYPE_LIST)
            .map(writer -> writer.createSpecific(bodyType))
            .orElse(null));
    }

    @SuppressWarnings("unchecked")
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
            messageBodyWriter.writeTo(bodyType, mediaType, data, outgoingHeaders, baos);
            body = baos.toByteArray();
        }

        writeComment(output, event.getComment());
        writeAttribute(output, ID_PREFIX, "id", event.getId());
        writeAttribute(output, EVENT_PREFIX, "event", event.getName());
        Duration retry = event.getRetry();
        if (retry != null) {
            writeAttribute(output, RETRY_PREFIX, "retry", String.valueOf(retry.toMillis()));
        }

        // Write each line of the data as its own field, including an empty last line
        writeLines(output, DATA_PREFIX, EMPTY_DATA, body);

        // Write a new line for event separation
        output.write(NEWLINE);
    }

    /**
     * Writes each line of the value as its own field. Lines end with a carriage return, a line
     * feed or both, and an empty last line is kept, so that a value ending in a line break, or an
     * empty value, is read back unchanged.
     *
     * @param output      The output
     * @param prefix      The prefix of a line with content
     * @param emptyPrefix The prefix of an empty line
     * @param value       The value
     */
    private static void writeLines(Output output, byte[] prefix, byte[] emptyPrefix, byte[] value) {
        int start = 0;
        while (true) {
            int end = lineEnd(value, start);
            int lineEnd = end == -1 ? value.length : end;
            if (lineEnd == start) {
                output.write(emptyPrefix);
            } else {
                output.write(prefix).write(value, start, lineEnd - start);
            }
            output.write(NEWLINE);
            if (end == -1) {
                return;
            }
            start = end + 1;
            if (value[end] == '\r' && start < value.length && value[start] == '\n') {
                start++;
            }
        }
    }

    private static int lineEnd(byte[] value, int start) {
        for (int i = start; i < value.length; i++) {
            byte b = value[i];
            if (b == '\n' || b == '\r') {
                return i;
            }
        }
        return -1;
    }

    private static void writeComment(Output output, @Nullable String comment) {
        if (comment != null) {
            writeLines(output, COMMENT_PREFIX, EMPTY_COMMENT, comment.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * @param eventData The byte buffer
     * @param attribute The attribute
     * @param field     The name of the field
     * @param value     The value
     */
    private static void writeAttribute(Output eventData, byte[] attribute, String field, @Nullable String value) {
        if (value != null) {
            if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                // a line break would end the field and let the value start another field or event
                throw new CodecException("The " + field + " of a server-sent event must not contain a line break");
            }
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
            buffer = bufferFactory.buffer(0);
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
