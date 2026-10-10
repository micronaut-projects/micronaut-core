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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.exceptions.UnsupportedMediaException;
import io.micronaut.http.simple.SimpleHttpRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JSON elements on a server without micronaut-http-netty: the readers live in json-core.
 */
class AsyncRequestBodyWithoutNettyTest {

    private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    @Test
    void theElementsOfJsonAreReadWithoutNetty() throws Exception {
        assertJsonElements("[{\"a\":1},{\"a\":2}]", MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    void theElementsOfAJsonStreamAreReadWithoutNetty() throws Exception {
        assertJsonElements("{\"a\":1}\n{\"a\":2}\n", MediaType.APPLICATION_JSON_STREAM_TYPE);
    }

    @Test
    void aMediaTypeThatHasNoElementsIsUnsupported() throws Exception {
        Throwable failure = firstElementFailure("a", MediaType.TEXT_PLAIN_TYPE);

        assertInstanceOf(UnsupportedMediaException.class, failure);
    }

    private static Throwable firstElementFailure(String content, MediaType contentType) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             OtherServerRequest server = request(content, contentType)) {
            DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));
            try (BodyElements<Map> elements = body.elements(Map.class)) {
                ExecutionException e = assertThrows(ExecutionException.class, () -> elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS));
                return e.getCause();
            } finally {
                body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void assertJsonElements(String content, MediaType contentType) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             OtherServerRequest server = request(content, contentType)) {
            DefaultAsyncRequestBody body = new DefaultAsyncRequestBody(server, server, ctx.getBean(AsyncRequestBodyArgumentBinder.class));
            try (BodyElements<Map> elements = body.elements(Map.class)) {
                assertEquals(Optional.of(Map.of("a", 1)), elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertEquals(Optional.of(Map.of("a", 2)), elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS));
                assertEquals(Optional.empty(), elements.next().toCompletableFuture().get(10, TimeUnit.SECONDS));
            } finally {
                body.releaseBody().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static OtherServerRequest request(String content, MediaType contentType) {
        SimpleHttpRequest<Object> request = new SimpleHttpRequest<>(HttpMethod.POST, "/body", null);
        request.contentType(contentType);
        return new OtherServerRequest(request, content);
    }

    /**
     * A server request whose bytes are byte arrays, like the request of a servlet server.
     */
    private static final class OtherServerRequest extends HttpRequestWrapper<Object> implements ServerHttpRequest<Object>, AutoCloseable {
        private final CloseableByteBody body;

        OtherServerRequest(HttpRequest<Object> delegate, String content) {
            super(delegate);
            this.body = BODIES.copyOf(content, StandardCharsets.UTF_8);
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
