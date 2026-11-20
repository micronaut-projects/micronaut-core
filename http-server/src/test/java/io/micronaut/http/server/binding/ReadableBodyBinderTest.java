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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.io.Readable;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadableBodyBinderTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private ApplicationContext ctx;
    private ReadableBodyBinder binder;

    @BeforeEach
    void setUp() {
        ctx = ApplicationContext.run();
        binder = new ReadableBodyBinder(new InputStreamBodyBinder(ctx.getBean(ServerBodyAnnotationBinder.class)));
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    void argumentType() {
        assertEquals(Readable.class, binder.argumentType().getType());
    }

    @Test
    void bindsTheBody() throws IOException {
        try (StreamingServerRequest server = request("hello", null, BodySizeLimits.UNLIMITED)) {
            Readable readable = bind(server).orElseThrow();
            assertTrue(readable.exists());
            assertEquals("/body", readable.getName());
            try (InputStream in = readable.asInputStream()) {
                assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void theReaderUsesTheCharsetOfTheRequest() throws IOException {
        String text = "héllo";
        Charset latin1 = StandardCharsets.ISO_8859_1;
        try (StreamingServerRequest server = request(text, latin1, BodySizeLimits.UNLIMITED)) {
            Readable readable = bind(server).orElseThrow();
            try (Reader reader = readable.asReader()) {
                StringWriter out = new StringWriter();
                reader.transferTo(out);
                assertEquals(text, out.toString());
            }
        }
    }

    @Test
    void anEmptyBodyIsNotBound() {
        try (StreamingServerRequest server = request("", null, BodySizeLimits.UNLIMITED)) {
            assertTrue(bind(server).isEmpty());
        }
    }

    @Test
    void aRequestWithoutABodyIsNotBound() {
        HttpRequest<?> plain = new SimpleHttpRequest<>(HttpMethod.POST, "/body", null);
        assertTrue(binder.bind(ConversionContext.of(ReadableBodyBinder.TYPE), plain).getValue().isEmpty());
    }

    @Test
    void aReadOverTheLimitThrowsTheContentLengthExceededException() throws IOException {
        try (StreamingServerRequest server = request("hello world", null, new BodySizeLimits(5, Integer.MAX_VALUE))) {
            Readable readable = bind(server).orElseThrow();
            try (InputStream in = readable.asInputStream()) {
                assertThrows(ContentLengthExceededException.class, in::readAllBytes);
            }
        }
    }

    private Optional<Readable> bind(StreamingServerRequest server) {
        return binder.bind(ConversionContext.of(ReadableBodyBinder.TYPE), new HttpRequestWrapper<>(server)).getValue();
    }

    private static StreamingServerRequest request(String text, Charset charset, BodySizeLimits limits) {
        Charset encoding = charset == null ? StandardCharsets.UTF_8 : charset;
        SimpleHttpRequest<Object> delegate = new SimpleHttpRequest<>(HttpMethod.POST, "/body", null);
        if (charset != null) {
            delegate.getHeaders().add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=" + charset.name());
        }
        CloseableByteBody body = text.isEmpty()
            ? BODIES.createEmpty()
            : BODIES.adapt(Flux.just(BODIES.readBufferFactory().copyOf(text, encoding)), limits, null, null);
        return new StreamingServerRequest(delegate, body);
    }

    private static final class StreamingServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body;

        StreamingServerRequest(HttpRequest<Object> delegate, CloseableByteBody body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public ByteBodyFactory byteBodyFactory() {
            return BODIES;
        }

        @Override
        public void close() {
            body.close();
        }
    }
}
