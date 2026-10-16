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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The stream of a {@code @Body InputStream} surfaces a read over the maximum request size as the
 * {@link ContentLengthExceededException} itself.
 */
class InputStreamBodyBinderTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void readsPassThrough() throws IOException {
        try (InputStream in = new InputStreamBodyBinder.SizeLimitInputStream(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))) {
            assertEquals('h', in.read());
            assertEquals(2, in.skip(2));
            byte[] b = new byte[4];
            assertEquals(2, in.read(b, 0, 4));
            assertArrayEquals(new byte[] {'l', 'o', 0, 0}, b);
            assertEquals(-1, in.read());
        }
    }

    @Test
    void aReadOverTheLimitThrowsTheContentLengthExceededException() {
        ContentLengthExceededException exceeded = new ContentLengthExceededException(1, 2);
        InputStream in = new InputStreamBodyBinder.SizeLimitInputStream(new FailingInputStream(new IOException("too large", exceeded)));

        assertSame(exceeded, assertThrows(ContentLengthExceededException.class, in::read));
        assertSame(exceeded, assertThrows(ContentLengthExceededException.class, () -> in.read(new byte[1], 0, 1)));
        assertSame(exceeded, assertThrows(ContentLengthExceededException.class, () -> in.skip(1)));
    }

    @Test
    void anyOtherReadFailureIsRethrownUnchanged() {
        IOException failure = new IOException("broken", new IllegalStateException());
        InputStream in = new InputStreamBodyBinder.SizeLimitInputStream(new FailingInputStream(failure));

        assertSame(failure, assertThrows(IOException.class, in::read));
        assertSame(failure, assertThrows(IOException.class, () -> in.read(new byte[1], 0, 1)));
        assertSame(failure, assertThrows(IOException.class, () -> in.skip(1)));
    }

    @Test
    void theBoundStreamOfABodyOverTheLimitThrowsTheContentLengthExceededException() throws IOException {
        CloseableByteBody body = BODIES.adapt(
            Flux.just(BODIES.readBufferFactory().copyOf("hello world", StandardCharsets.UTF_8)),
            new BodySizeLimits(5, Integer.MAX_VALUE), null, null);
        try (ApplicationContext ctx = ApplicationContext.run();
             StreamingServerRequest server = new StreamingServerRequest(new SimpleHttpRequest<>(HttpMethod.POST, "/body", null), body)) {
            InputStreamBodyBinder binder = new InputStreamBodyBinder(ctx.getBean(ServerBodyAnnotationBinder.class));
            try (InputStream input = binder.bind(ConversionContext.of(InputStream.class), new HttpRequestWrapper<>(server)).getValue().orElseThrow()) {
                assertThrows(ContentLengthExceededException.class, input::readAllBytes);
            }
        }
    }

    private static final class FailingInputStream extends InputStream {
        private final IOException failure;

        FailingInputStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public int read() throws IOException {
            throw failure;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            throw failure;
        }

        @Override
        public long skip(long n) throws IOException {
            throw failure;
        }
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
