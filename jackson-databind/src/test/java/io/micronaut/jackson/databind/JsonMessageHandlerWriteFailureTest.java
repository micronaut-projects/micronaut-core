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
package io.micronaut.jackson.databind;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.codec.CodecException;
import io.micronaut.json.body.JsonMessageHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Jackson 3 reports a failure while serializing, like a getter that throws, with an unchecked
 * exception. The handler reports it as a {@link CodecException}, like an I/O failure.
 */
class JsonMessageHandlerWriteFailureTest {

    private final JsonMessageHandler<Failing> handler = new JsonMessageHandler<>(new JacksonDatabindMapper());
    private final ByteBodyFactory bodyFactory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private final Argument<Failing> type = Argument.of(Failing.class);

    @Test
    void writeTo() {
        MutableHttpHeaders headers = HttpResponse.ok().getHeaders();
        CodecException e = assertThrows(CodecException.class, () -> handler.writeTo(type, MediaType.APPLICATION_JSON_TYPE, new Failing(), headers, new ByteArrayOutputStream()));
        assertEquals("foo", rootCause(e).getMessage());
    }

    @Test
    void write() {
        CodecException e = assertThrows(CodecException.class, () -> handler.write(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), type, MediaType.APPLICATION_JSON_TYPE, new Failing()));
        assertEquals("foo", rootCause(e).getMessage());
    }

    @Test
    void writePiece() {
        CodecException e = assertThrows(CodecException.class, () -> handler.writePiece(bodyFactory, HttpRequest.GET("/"), HttpResponse.ok(), type, MediaType.APPLICATION_JSON_TYPE, new Failing()));
        assertEquals("foo", rootCause(e).getMessage());
    }

    private static Throwable rootCause(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    public static final class Failing {
        public String getName() {
            throw new IllegalStateException("foo");
        }
    }
}
