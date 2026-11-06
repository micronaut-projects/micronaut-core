package io.micronaut.http.body;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageBodyWriterByteArrayBufferTest {

    @Test
    void writeToByteArrayBufferFactory() {
        MessageBodyWriter<String> writer = new MessageBodyWriter<>() {
            @Override
            public void writeTo(Argument<String> type, MediaType mediaType, String object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
                try {
                    outputStream.write(object.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new CodecException("Failed to write", e);
                }
            }
        };
        String body = "{\"message\":\"" + "x".repeat(10_000) + "\"}";

        ByteBuffer<?> buffer = writer.writeTo(Argument.STRING, MediaType.APPLICATION_JSON_TYPE, body, new SimpleHttpHeaders(), ByteArrayBufferFactory.INSTANCE);

        assertEquals(body, buffer.toString(StandardCharsets.UTF_8));
    }
}
